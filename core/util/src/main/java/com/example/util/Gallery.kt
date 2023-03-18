package com.example.util

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.ui.GalleryImage
import com.example.ui.GalleryState
import com.example.ui.theme.Elevation
import kotlin.math.max

@Composable
fun Gallery(
    modifier: Modifier = Modifier,
    images: List<Uri>,
    imageSize: Dp = 40.dp,
    spaceBetween: Dp = 10.dp,
    imageShape: CornerBasedShape = Shapes().small
) {
    BoxWithConstraints(modifier = modifier) {
        val numberOfVisibleImages by remember {
            derivedStateOf {
                max(
                    a = 0,
                    b = this.maxWidth.div(imageSize + spaceBetween).toInt().minus(1)
                )
            }
        }

        val numberOfRemainingImages by remember {
            derivedStateOf {
                images.size - numberOfVisibleImages
            }
        }

        Row {
            images.take(numberOfVisibleImages).forEach { imageUrl ->
                AsyncImage(
                    modifier = Modifier
                        .clip(shape = imageShape)
                        .size(imageSize),
                    contentScale = ContentScale.Crop,
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(imageUrl)
                        .crossfade(true)
                        .build(), contentDescription = "Gallery image"
                )

                Spacer(modifier = Modifier.width(spaceBetween))

                if (numberOfRemainingImages > 0) {
                    LastImageOverlay(
                        imageSize = imageSize,
                        imageShape = imageShape,
                        remainingImages = numberOfRemainingImages
                    )
                }
            }
        }
    }
}

@Composable
fun GalleryUploader(
    modifier: Modifier = Modifier,
    galleryState: GalleryState,
    imageSize: Dp = 60.dp,
    imageShape: CornerBasedShape = Shapes().medium,
    spaceBetween: Dp = 12.dp,
    onAddClicked: () -> Unit,
    onImageSelected: (Uri) -> Unit,
    onImageClicked: (GalleryImage) -> Unit,
) {
    val multiplePhotoPicker =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.PickMultipleVisualMedia()
        ) { images ->
            images.forEach {
                onImageSelected(it)
            }
        }

    BoxWithConstraints(modifier = modifier) {
        val numberOfVisibleImages by remember {
            derivedStateOf {
                max(
                    a = 0,
                    b = this.maxWidth.div(imageSize + spaceBetween).toInt().minus(2)
                )
            }
        }

        val numberOfRemainingImages by remember {
            derivedStateOf {
                galleryState.images.size - numberOfVisibleImages
            }
        }

        Row {

            AddImageButton(
                imageSize = imageSize,
                imageShape = imageShape,
                onClick = {
                    onAddClicked()
                    multiplePhotoPicker.launch(
                        PickVisualMediaRequest(
                            ActivityResultContracts.PickVisualMedia.ImageOnly
                        )
                    )
                }
            )

            Spacer(modifier = Modifier.width(spaceBetween))

            galleryState.images.take(numberOfVisibleImages).forEach { gallerImage ->
                AsyncImage(
                    modifier = Modifier
                        .clip(shape = imageShape)
                        .size(imageSize)
                        .clickable { onImageClicked(gallerImage) },
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(gallerImage.image)
                        .crossfade(true)
                        .build(), contentDescription = "Gallery image",
                    contentScale = ContentScale.Crop
                )

                Spacer(modifier = Modifier.width(spaceBetween))

                if (numberOfRemainingImages > 0) {
                    LastImageOverlay(
                        imageSize = imageSize,
                        imageShape = imageShape,
                        remainingImages = numberOfRemainingImages
                    )
                }
            }
        }
    }
}

@Composable
fun LastImageOverlay(
    imageSize: Dp,
    imageShape: CornerBasedShape,
    remainingImages: Int
) {
    Box(contentAlignment = Alignment.Center) {
        Surface(
            modifier = Modifier
                .clip(imageShape)
                .size(imageSize),
            color = MaterialTheme.colorScheme.primaryContainer
        ) {}
        Text(
            text = "+$remainingImages",
            style = TextStyle(
                fontSize = MaterialTheme.typography.titleLarge.fontSize,
                fontWeight = FontWeight.Bold
            ),
            color = MaterialTheme.colorScheme.onPrimaryContainer
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddImageButton(
    imageSize: Dp,
    imageShape: CornerBasedShape,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .size(imageSize)
            .clip(shape = imageShape),
        onClick = onClick,
        tonalElevation = Elevation.Level1
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Icon(imageVector = Icons.Default.Add, contentDescription = "Add icon")
        }
    }
}