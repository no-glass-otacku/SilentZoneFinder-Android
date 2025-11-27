package com.example.silentzonefinder_android.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.example.silentzonefinder_android.data.ReviewImage
import com.example.silentzonefinder_android.databinding.ItemReviewImageBinding

class ReviewImageAdapter(
    private val images: MutableList<ReviewImage>,
    private val onDeleteClicked: (ReviewImage) -> Unit // 이미지 삭제 클릭 리스너
) : RecyclerView.Adapter<ReviewImageAdapter.ImageViewHolder>() {

    class ImageViewHolder(private val binding: ItemReviewImageBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(reviewImage: ReviewImage, onDeleteClicked: (ReviewImage) -> Unit) {
            // ✅ Coil 라이브러리로 URI를 ImageView에 로드 (코드가 훨씬 간결해집니다)
            binding.imageView.load(reviewImage.uri) {
                crossfade(true) // 부드러운 이미지 전환 효과 (선택사항)
//                placeholder(R.drawable.placeholder_image) // 로딩 중 보여줄 이미지 (선택사항)
//                error(R.drawable.error_image) // 에러 시 보여줄 이미지 (선택사항)
//                transformations(coil.transform.CircleCropTransformation()) // 원형으로 자르기 (선택사항)
            }

            // 삭제 버튼 리스너
            binding.btnDelete.setOnClickListener {
                onDeleteClicked(reviewImage)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ImageViewHolder {
        val binding = ItemReviewImageBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ImageViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ImageViewHolder, position: Int) {
        holder.bind(images[position], onDeleteClicked)
    }

    override fun getItemCount() = images.size
}