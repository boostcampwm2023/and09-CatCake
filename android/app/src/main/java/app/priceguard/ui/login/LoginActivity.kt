package app.priceguard.ui.login

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import app.priceguard.R
import app.priceguard.databinding.ActivityLoginBinding
import app.priceguard.ui.home.HomeActivity
import app.priceguard.ui.login.LoginViewModel.LoginEvent
import app.priceguard.ui.login.findpassword.FindPasswordActivity
import app.priceguard.ui.signup.SignupActivity
import app.priceguard.ui.util.SystemNavigationColorState
import app.priceguard.ui.util.applySystemNavigationBarColor
import app.priceguard.ui.util.drawable.getCircularProgressIndicatorDrawable
import app.priceguard.ui.util.lifecycle.repeatOnStarted
import app.priceguard.ui.util.showConfirmDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.progressindicator.CircularProgressIndicatorSpec
import com.google.android.material.progressindicator.IndeterminateDrawable
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class LoginActivity : AppCompatActivity() {
    private val loginViewModel: LoginViewModel by viewModels()
    private lateinit var binding: ActivityLoginBinding
    private lateinit var circularProgressIndicator: IndeterminateDrawable<CircularProgressIndicatorSpec>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        this.applySystemNavigationBarColor(SystemNavigationColorState.SURFACE)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        with(binding) {
            viewModel = loginViewModel
        }
        circularProgressIndicator = getCircularProgressIndicatorDrawable(this@LoginActivity)
        initListener()
        collectEvent()
        setContentView(binding.root)
    }

    override fun onDestroy() {
        super.onDestroy()
        circularProgressIndicator.stop()
    }

    private fun initListener() {
        with(binding) {
            btnLoginSignup.setOnClickListener {
                gotoSignUp()
            }
            tvFindPassword.setOnClickListener {
                gotoFindPassword()
            }
        }
    }

    private fun collectEvent() {
        repeatOnStarted {
            loginViewModel.event.collect { event ->
                when (event) {
                    LoginEvent.LoginStart -> {
                        (binding.btnLoginLogin as MaterialButton).icon = circularProgressIndicator
                    }

                    LoginEvent.TokenUpdateError, LoginEvent.FirebaseError -> {
                        Toast.makeText(
                            this@LoginActivity,
                            getString(R.string.push_notification_not_working),
                            Toast.LENGTH_LONG
                        ).show()
                    }

                    else -> {
                        (binding.btnLoginLogin as MaterialButton).icon = null
                        setDialogMessageAndShow(event)
                    }
                }
            }
        }
    }

    private fun setDialogMessageAndShow(eventType: LoginEvent) {
        when (eventType) {
            LoginEvent.Invalid -> {
                showConfirmDialog(
                    getString(R.string.login_invalid),
                    getString(R.string.login_invalid_message)
                )
            }

            is LoginEvent.LoginFailure -> {
                showConfirmDialog(
                    getString(R.string.login_fail),
                    getString(R.string.login_fail_message)
                )
            }

            is LoginEvent.UndefinedError -> {
                showConfirmDialog(
                    getString(R.string.login_fail),
                    getString(R.string.undefined_error)
                )
            }

            is LoginEvent.LoginInfoSaved -> {
                gotoHomeActivity()
            }

            else -> {}
        }
    }

    private fun gotoSignUp() {
        startActivity(Intent(this, SignupActivity::class.java))
    }

    private fun gotoFindPassword() {
        startActivity(Intent(this, FindPasswordActivity::class.java))
    }

    private fun gotoHomeActivity() {
        val intent = Intent(this, HomeActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }
}
