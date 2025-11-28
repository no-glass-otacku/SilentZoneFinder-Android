package com.example.silentzonefinder_android.fragment

import android.app.Dialog
import android.os.Bundle
import androidx.fragment.app.DialogFragment
import com.example.silentzonefinder_android.R
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class ImageSourceDialogFragment : DialogFragment() {

    interface OnSourceSelectedListener {
        fun onCameraSelected()
        fun onGallerySelected()
    }

    private var listener: OnSourceSelectedListener? = null

    fun setOnSourceSelectedListener(listener: OnSourceSelectedListener) {
        this.listener = listener
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val hasCamera = arguments?.getBoolean(ARG_HAS_CAMERA, true) ?: true
        val title = arguments?.getString(ARG_TITLE) ?: getString(R.string.select_image_source)

        val items = if (hasCamera) {
            arrayOf(getString(R.string.camera), getString(R.string.gallery))
        } else {
            arrayOf(getString(R.string.gallery))
        }

        return MaterialAlertDialogBuilder(requireContext())
            .setTitle(title)
            .setItems(items) { _, which ->
                when {
                    hasCamera && which == 0 -> listener?.onCameraSelected()
                    hasCamera && which == 1 -> listener?.onGallerySelected()
                    !hasCamera && which == 0 -> listener?.onGallerySelected()
                }
                dismiss()
            }
            .setNegativeButton(getString(R.string.cancel)) { _, _ ->
                dismiss()
            }
            .create()
    }

    companion object {
        private const val ARG_TITLE = "title"
        private const val ARG_HAS_CAMERA = "has_camera"

        fun newInstance(
            title: String? = null,
            hasCamera: Boolean = true
        ): ImageSourceDialogFragment {
            return ImageSourceDialogFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_TITLE, title)
                    putBoolean(ARG_HAS_CAMERA, hasCamera)
                }
            }
        }
    }
}

