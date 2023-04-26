package me.stageguard.aruku.ui

/**
 * Annotate that this field is a ui state that representing
 * data binding between view model and view.
 */
@Retention(AnnotationRetention.SOURCE)
@Target(AnnotationTarget.FIELD, AnnotationTarget.PROPERTY)
annotation class UiState()
