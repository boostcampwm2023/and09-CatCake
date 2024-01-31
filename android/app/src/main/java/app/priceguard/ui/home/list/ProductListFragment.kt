package app.priceguard.ui.home.list

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.work.WorkManager
import app.priceguard.R
import app.priceguard.data.repository.product.ProductErrorState
import app.priceguard.data.repository.token.TokenRepository
import app.priceguard.databinding.FragmentProductListBinding
import app.priceguard.service.UpdateAlarmWorker
import app.priceguard.ui.additem.AddItemActivity
import app.priceguard.ui.detail.DetailActivity
import app.priceguard.ui.home.ProductSummaryAdapter
import app.priceguard.ui.home.ProductSummaryClickListener
import app.priceguard.ui.util.drawable.getCircularProgressIndicatorDrawable
import app.priceguard.ui.util.lifecycle.repeatOnStarted
import app.priceguard.ui.util.showDialogWithAction
import app.priceguard.ui.util.showDialogWithLogout
import com.google.android.material.progressindicator.CircularProgressIndicatorSpec
import com.google.android.material.progressindicator.IndeterminateDrawable
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class ProductListFragment : Fragment() {

    @Inject
    lateinit var tokenRepository: TokenRepository

    private var _binding: FragmentProductListBinding? = null
    private val binding get() = _binding!!
    private val productListViewModel: ProductListViewModel by viewModels()

    private var workRequestSet: MutableSet<String> = mutableSetOf()
    private lateinit var circularProgressIndicator: IndeterminateDrawable<CircularProgressIndicatorSpec>

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentProductListBinding.inflate(layoutInflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        circularProgressIndicator = getCircularProgressIndicatorDrawable(view.context, R.style.Theme_PriceGuard_CircularProgressLoading)
        binding.viewModel = productListViewModel
        binding.lifecycleOwner = viewLifecycleOwner
        binding.initSettingAdapter()
        binding.initListener()
        collectEvent()
    }

    override fun onStart() {
        super.onStart()
        productListViewModel.getProductList(false)
    }

    override fun onDestroy() {
        super.onDestroy()
        circularProgressIndicator.stop()
    }

    private fun FragmentProductListBinding.initSettingAdapter() {
        val listener = object : ProductSummaryClickListener {
            override fun onClick(productCode: String) {
                val intent = Intent(context, DetailActivity::class.java)
                intent.putExtra("productCode", productCode)
                startActivity(intent)
            }

            override fun onToggle(productCode: String, checked: Boolean) {
                productListViewModel.updateProductAlarmToggle(productCode, checked)
                if (workRequestSet.contains(productCode)) {
                    workRequestSet.remove(productCode)
                } else {
                    workRequestSet.add(productCode)
                }
            }
        }

        val adapter = ProductSummaryAdapter(listener, ProductSummaryAdapter.userDiffUtil)
        rvProductList.adapter = adapter
        this@ProductListFragment.repeatOnStarted {
            productListViewModel.state.collect { state ->
                if (state.productList.isEmpty()) {
                    binding.loadingLayoutProductList.visibility = View.VISIBLE
                    binding.rvProductList.visibility = View.GONE
                    if (state.isUpdated) {
                        binding.noProductProductList.visibility = View.VISIBLE
                        binding.loadingSpinnerProductList.visibility = View.GONE
                    } else {
                        binding.noProductProductList.visibility = View.GONE
                        binding.loadingSpinnerProductList.visibility = View.VISIBLE
                        binding.loadingSpinnerProductList.setImageDrawable(circularProgressIndicator)
                    }
                } else {
                    binding.loadingLayoutProductList.visibility = View.GONE
                    binding.rvProductList.visibility = View.VISIBLE
                    adapter.submitList(state.productList)
                }
            }
        }
    }

    private fun FragmentProductListBinding.initListener() {
        fabProductList.setOnClickListener {
            gotoProductAddActivity()
        }

        ablProductList.addOnOffsetChangedListener { _, verticalOffset ->
            srlProductList.isEnabled = verticalOffset == 0
        }
    }

    private fun gotoProductAddActivity() {
        val intent = Intent(activity, AddItemActivity::class.java)
        startActivity(intent)
    }

    private fun collectEvent() {
        viewLifecycleOwner.repeatOnStarted {
            productListViewModel.events.collect { event ->
                when (event) {
                    ProductErrorState.PERMISSION_DENIED -> {
                        showDialogWithLogout()
                    }

                    ProductErrorState.INVALID_REQUEST -> {
                        showDialogWithAction(
                            getString(R.string.product_list_failed),
                            getString(R.string.invalid_request)
                        )
                    }

                    ProductErrorState.NOT_FOUND -> {
                        showDialogWithAction(
                            getString(R.string.product_list_failed),
                            getString(R.string.not_found)
                        )
                    }

                    else -> {
                        showDialogWithAction(
                            getString(R.string.product_list_failed),
                            getString(R.string.undefined_error)
                        )
                    }
                }
            }
        }
    }

    override fun onStop() {
        super.onStop()
        workRequestSet.forEach { productCode ->
            WorkManager.getInstance(requireContext())
                .enqueue(UpdateAlarmWorker.createWorkRequest(productCode))
        }
        workRequestSet.clear()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding.rvProductList.adapter = null
        _binding = null
    }
}
