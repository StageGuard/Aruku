package me.stageguard.aruku.util

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Android
import androidx.compose.material.icons.outlined.AudioFile
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.FilePresent
import androidx.compose.material.icons.outlined.FolderZip
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.InsertDriveFile
import androidx.compose.material.icons.outlined.VideoFile
import java.text.DecimalFormat

// from ojhdt
fun Icons.Outlined.getFileIcon(extension: String) = when (extension) {
    "apk" -> Android
    "bmp", "jpeg", "jpg", "png", "tif", "gif", "pcx", "tga", "exif", "fpx",
    "svg", "psd", "cdr", "pcd", "dxf", "ufo", "eps", "ai", "raw", "webp",
    "avif", "apng", "tiff" -> Image
    "txt", "log", "md", "json", "xml" -> Description
    "cd", "wav", "aiff", "mp3", "wma", "ogg", "mpc", "flac", "ape", "3gp" -> AudioFile
    "avi", "wmv", "mp4", "mpeg", "mpg", "mov", "flv", "rmvb", "rm", "asf" -> VideoFile
    "zip", "rar", "7z", "bz2", "tar", "jar", "gz", "deb" -> FolderZip
    "docx", "doc", "xls", "xlsx", "ppt", "pptx" -> InsertDriveFile
    else -> FilePresent
}

// from ojhdt
fun Long.formatFileSize(): String {
    val format = DecimalFormat("#.##")
    return when (coerceAtLeast(0)) {
        in 0 until 1024 -> "${this}B"
        in 1024 until 1048576 -> "${format.format(toDouble() / 1024)}KB"
        in 1048576 until 1073741824 -> "${format.format(toDouble() / 1048576)}MB"
        else -> "${format.format(toDouble() / 1073741824)}GB"
    }
}