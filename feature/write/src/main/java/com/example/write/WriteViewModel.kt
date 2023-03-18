package com.example.write

import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mongo.database.ImageToDeleteDao
import com.example.mongo.database.ImageToUploadDao
import com.example.mongo.database.entity.ImageToDelete
import com.example.mongo.database.entity.ImageToUpload
import com.example.mongo.repository.MongoDB
import com.example.ui.GalleryImage
import com.example.ui.GalleryState
import com.example.util.Constants.WRITE_SCREEN_ARGUMENT_KEY
import com.example.util.fetchImagesFromFirebase
import com.example.util.model.Diary
import com.example.util.model.Mood
import com.example.util.model.RequestState
import com.example.util.toRealmInstant
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.storage.FirebaseStorage
import dagger.hilt.android.lifecycle.HiltViewModel
import io.realm.kotlin.types.ObjectId
import io.realm.kotlin.types.RealmInstant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.ZonedDateTime
import javax.inject.Inject

@HiltViewModel
internal class WriteViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val imageToUploadDao: ImageToUploadDao,
    private val imageToDeleteDao: ImageToDeleteDao
) : ViewModel() {

    var uiState by mutableStateOf(UiState())
        private set

    var galleryState = GalleryState()

    init {
        getDiaryIdArgument()
        fetchSelectedDiary()
    }

    private fun getDiaryIdArgument() {
        uiState = uiState.copy(
            selectedDiaryId = savedStateHandle.get<String>(
                key = WRITE_SCREEN_ARGUMENT_KEY
            )
        )
    }

    private fun fetchSelectedDiary() {
        uiState.selectedDiaryId?.let {
            viewModelScope.launch {
                MongoDB.getSelectedDiary(
                    diaryId = ObjectId.Companion.from(it)
                ).catch {
                    emit(RequestState.Error(Exception("Diary already deleted")))
                }.collect { diary ->
                    if (diary is RequestState.Success) {
                        setSelectedDiary(diary = diary.data)
                        setTitle(diary.data.title)
                        setDescription(description = diary.data.description)
                        setMood(mood = Mood.valueOf(diary.data.mood))

                        fetchImagesFromFirebase(
                            remoteImagePaths = diary.data.images,
                            onImageDownload = { downloadImage ->
                                galleryState.addImage(
                                    GalleryImage(
                                        image = downloadImage,
                                        remoteImagePath = extractImagePath(
                                            fullImagePath = downloadImage.toString()
                                        )
                                    )
                                )
                            }
                        )
                    }
                }
            }
        }
    }

    private fun extractImagePath(fullImagePath: String): String {
        val chunks = fullImagePath.split("%2F")
        val imageName = chunks[2].split("?").first()
        return "images/${FirebaseAuth.getInstance().currentUser?.uid}/$imageName"
    }

    fun setTitle(title: String) {
        uiState = uiState.copy(
            title = title
        )
    }

    fun setDescription(description: String) {
        uiState = uiState.copy(
            description = description
        )
    }

    private fun setMood(mood: Mood) {
        uiState = uiState.copy(
            mood = mood
        )
    }

    fun updateDateTime(zonedDateTime: ZonedDateTime) {
        uiState = uiState.copy(
            updatedDateTime = zonedDateTime.toInstant().toRealmInstant()
        )
    }


    private fun setSelectedDiary(diary: Diary) {
        uiState = uiState.copy(
            selectedDiary = diary
        )
    }

    fun upsertDiary(
        diary: Diary,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            if (uiState.selectedDiary != null) {
                updateDiary(diary = diary, onSuccess = onSuccess, onError = onError)
            } else {
                insertDiary(diary = diary, onSuccess = onSuccess, onError = onError)
            }
        }
    }

    private suspend fun insertDiary(
        diary: Diary,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        val result = MongoDB.insertDiary(diary = diary.apply {
            if (uiState.updatedDateTime != null) {
                date = uiState.updatedDateTime!!
            }
        })
        if (result is RequestState.Success) {
            uploadImageToFirebase()
            withContext(Dispatchers.Main) {
                onSuccess()
            }
        } else if (result is RequestState.Error) {
            withContext(Dispatchers.Main) {
                onError(result.error.message.toString())
            }
        }
    }

    private suspend fun updateDiary(
        diary: Diary,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        val result = MongoDB.updateDiary(diary = diary.apply {
            _id = ObjectId.Companion.from(uiState.selectedDiaryId!!)
            date = if (uiState.updatedDateTime != null) {
                uiState.updatedDateTime!!
            } else {
                uiState.selectedDiary!!.date
            }
        })
        if (result is RequestState.Success) {
            uploadImageToFirebase()
            deleteImagesFromFirebase()
            withContext(Dispatchers.Main) {
                onSuccess()
            }
        } else if (result is RequestState.Error) {
            withContext(Dispatchers.Main) {
                onError(result.error.message.toString())
            }
        }
    }

    fun deleteDiary(
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            uiState.selectedDiaryId?.let { id ->
                when (
                    val result = MongoDB.deleteDiary(id = ObjectId.Companion.from(id))
                ) {
                    is RequestState.Success -> {
                        uiState.selectedDiary?.let {
                            deleteImagesFromFirebase(it.images)
                        }
                        onSuccess()
                    }

                    is RequestState.Error -> {
                        onError(result.error.message.toString())
                    }

                    else -> {
                        // Do nothing
                    }
                }
            }
        }
    }

    fun addImage(image: Uri, imageType: String) {
        val remotePath = "images/${FirebaseAuth.getInstance().currentUser?.uid}/" +
                "${image.lastPathSegment}-${System.currentTimeMillis()}.$imageType"
        galleryState.addImage(
            GalleryImage(image = image, remoteImagePath = remotePath)
        )
    }

    private fun uploadImageToFirebase() {
        val storage = FirebaseStorage.getInstance().reference
        galleryState.images.forEach { galleryImage ->
            val imagePath = storage.child(galleryImage.remoteImagePath)
            imagePath.putFile(galleryImage.image).addOnProgressListener {
                val sessionUrl = it.uploadSessionUri
                if (sessionUrl != null) {
                    viewModelScope.launch {
                        imageToUploadDao.addImageToUpload(
                            ImageToUpload(
                                remoteImagePath = galleryImage.remoteImagePath,
                                imageUri = galleryImage.image.toString(),
                                sessionUri = sessionUrl.toString()
                            )
                        )
                    }
                }
            }
        }
    }

    private fun deleteImagesFromFirebase(images: List<String>? = null) {
        val storage = FirebaseStorage.getInstance().reference
        images?.let {
            images.forEach { remoteImagePath ->
                storage.child(remoteImagePath).delete().addOnFailureListener {
                    viewModelScope.launch {
                        imageToDeleteDao.addImageToDelete(ImageToDelete(remoteImagePath = remoteImagePath))
                    }
                }
            }
        }
        galleryState.imagesToBeDeleted.map { it.remoteImagePath }.forEach {remoteImagePath ->
            storage.child(remoteImagePath).delete().addOnFailureListener {
                viewModelScope.launch {
                    imageToDeleteDao.addImageToDelete(ImageToDelete(remoteImagePath = remoteImagePath))
                }
            }
        }
    }
}

data class UiState(
    val selectedDiaryId: String? = null,
    val title: String = "",
    val description: String = "",
    val mood: Mood = Mood.Neutral,
    val selectedDiary: Diary? = null,
    val updatedDateTime: RealmInstant? = null
)