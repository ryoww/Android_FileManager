package com.ryo.androidfilemanager.data.model

sealed class ViewerType(val displayName: String) {
    data object Pdf : ViewerType("PDF")
    data object Image : ViewerType("Image")
    data object Video : ViewerType("Video")
    data object Audio : ViewerType("Audio")
    data object Text : ViewerType("Text")
    data object Code : ViewerType("Code")
    data object Unsupported : ViewerType("Unsupported")
}

