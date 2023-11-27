package app.priceguard.ui.home

import android.content.Intent
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import app.priceguard.R
import app.priceguard.databinding.ItemProductSummaryBinding
import app.priceguard.ui.detail.DetailActivity

class ProductSummaryAdapter :
    ListAdapter<ProductSummary, ProductSummaryAdapter.ViewHolder>(diffUtil) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding =
            ItemProductSummaryBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = currentList[position]
        holder.bind(item)
    }

    class ViewHolder(private val binding: ItemProductSummaryBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: ProductSummary) {
            with(binding) {
                summary = item
                setViewType(item)
                setClickListener(item.productCode)
            }
        }

        private fun ItemProductSummaryBinding.setViewType(item: ProductSummary) {
            when (item) {
                is ProductSummary.RecommendedProductSummary -> {
                    tvProductRecommendRank.visibility = View.VISIBLE
                    msProduct.visibility = View.GONE
                    tvProductDiscountPercent.visibility = View.GONE
                    setRecommendRank(item)
                }

                is ProductSummary.UserProductSummary -> {
                    tvProductRecommendRank.visibility = View.GONE
                    msProduct.visibility = View.VISIBLE
                    tvProductDiscountPercent.visibility = View.VISIBLE
                    setDisCount(item.discountPercent)
                    setSwitchListener()
                }
            }
        }

        private fun ItemProductSummaryBinding.setSwitchListener() {
            if (msProduct.isChecked.not()) {
                msProduct.setThumbIconResource(R.drawable.ic_notifications_off)
            }
            msProduct.setOnCheckedChangeListener { _, isChecked ->
                if (isChecked) {
                    // TODO: 푸쉬 알람 설정 추가
                    msProduct.setThumbIconResource(R.drawable.ic_notifications_active)
                } else {
                    // TODO: 푸쉬 알람 설정 제거
                    msProduct.setThumbIconResource(R.drawable.ic_notifications_off)
                }
            }
        }

        private fun ItemProductSummaryBinding.setDisCount(discount: Float) {
            tvProductDiscountPercent.text =
                if (discount > 0) {
                    tvProductDiscountPercent.context.getString(
                        R.string.add_plus,
                        tvProductDiscountPercent.context.getString(R.string.percent, discount)
                    )
                } else {
                    tvProductDiscountPercent.context.getString(
                        R.string.percent,
                        discount
                    )
                }
            val value = TypedValue()
            tvProductDiscountPercent.context.theme.resolveAttribute(
                if (discount > 0) android.R.attr.colorPrimary else android.R.attr.colorError,
                value,
                true
            )
            tvProductDiscountPercent.setTextColor(value.data)
        }

        private fun ItemProductSummaryBinding.setRecommendRank(item: ProductSummary.RecommendedProductSummary) {
            tvProductRecommendRank.text = tvProductRecommendRank.context.getString(
                R.string.recommand_rank, item.recommendRank
            )
        }

        private fun ItemProductSummaryBinding.setClickListener(code: String) {
            cvProduct.setOnClickListener {
                val intent = Intent(binding.root.context, DetailActivity::class.java)
                intent.putExtra("productCode", code)
                binding.root.context.startActivity(intent)
            }
        }
    }

    companion object {
        val diffUtil = object : DiffUtil.ItemCallback<ProductSummary>() {
            override fun areContentsTheSame(oldItem: ProductSummary, newItem: ProductSummary) =
                oldItem == newItem

            override fun areItemsTheSame(oldItem: ProductSummary, newItem: ProductSummary) =
                oldItem.hashCode() == newItem.hashCode()
        }
    }
}
