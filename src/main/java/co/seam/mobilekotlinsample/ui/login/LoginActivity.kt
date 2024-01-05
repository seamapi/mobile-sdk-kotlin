package co.seam.mobilekotlinsample.ui.login

import android.app.Activity
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import android.os.Bundle
import android.os.Looper
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.LinearLayout.LayoutParams
import android.widget.TextView
import android.widget.Toast
import co.seam.abloy.SeamIntegrationAssaAbloyConfiguration
import co.seam.mobilekotlinsample.databinding.ActivityLoginBinding

import co.seam.mobilekotlinsample.R
import co.seam.sdk.SeamDeviceCommand
import co.seam.sdk.SeamDeviceController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class LoginActivity : AppCompatActivity() {

    private lateinit var loginViewModel: LoginViewModel
    private lateinit var binding: ActivityLoginBinding

    private val coroutineScope = CoroutineScope(Dispatchers.IO)
    private var seamDeviceController: SeamDeviceController? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)
        val loginActivity = this

        val username = binding.username
        val password = binding.password
        val login = binding.login
        val loading = binding.loading
        val unlockNearestButton = binding.unlockNearestButton
        val myCredentialContainer = binding.myCredentialContainer

        unlockNearestButton?.isEnabled = false

        loginViewModel = ViewModelProvider(this, LoginViewModelFactory())
                .get(LoginViewModel::class.java)


        loginViewModel.loginFormState.observe(this@LoginActivity, Observer {
            val loginState = it ?: return@Observer

            // disable login button unless both username / password is valid
            login.isEnabled = loginState.isDataValid && (!loginState.loginCompleted)

            if (loginState.usernameError != null) {
                username.error = getString(loginState.usernameError)
            }
            if (loginState.passwordError != null) {
                password.error = getString(loginState.passwordError)
            }
        })

      unlockNearestButton?.setOnClickListener {
        runBlocking {
          val res = seamDeviceController?.issueCommand(SeamDeviceCommand.UNLOCK_NEAREST)

          if (res?.isSuccess == true) {
            Toast.makeText(applicationContext, "Unlocked Entrance!", Toast.LENGTH_LONG).show()
          } else {
            Toast.makeText(applicationContext, "Didn't unlock anything...", Toast.LENGTH_LONG).show()
          }
        }
      }

        loginViewModel.loginResult.observe(this@LoginActivity, Observer {
            val loginResult = it ?: return@Observer

            loading.visibility = View.GONE
            if (loginResult.error != null) {
                showLoginFailed(loginResult.error)
            }
            if (loginResult.success != null) {
                updateUiWithUser(loginResult.success)
            }
            setResult(Activity.RESULT_OK)

            //Complete and destroy login activity once successful
            finish()
        })

        username.afterTextChanged {
            loginViewModel.loginDataChanged(
                    username.text.toString(),
                    password.text.toString()
            )
        }

        password.apply {
            afterTextChanged {
                loginViewModel.loginDataChanged(
                        username.text.toString(),
                        password.text.toString()
                )
            }

            setOnEditorActionListener { _, actionId, _ ->
                when (actionId) {
                    EditorInfo.IME_ACTION_DONE ->
                        loginViewModel.login(
                                username.text.toString(),
                                password.text.toString()
                        )
                }
                false
            }

            login.setOnClickListener {
                loading.visibility = View.VISIBLE

              Toast.makeText(applicationContext, "Got Client Session Token from server, now getting credentials....", Toast.LENGTH_LONG).show()

              // Make keyboard collapse

              // Disable buttons
              login.isEnabled = false

              var credentialsLoaded = false;

              if (seamDeviceController == null) {

                val clientSessionTokenFromServer = password.text.toString().trim()
                System.out.println("Provided Client Session Token: \"$clientSessionTokenFromServer\"")

                seamDeviceController =
                  SeamDeviceController(
                    clientSessionTokenFromServer,
                    context,

                    // Configuration!
                    integrationProviders = listOf(SeamIntegrationAssaAbloyConfiguration(applicationId = "1234", applicationDescription = "Test", lockServiceCodes = listOf(1, 2, 3, 4))),
                    backgroundScope = coroutineScope,
                    baseUrl = "https://connect.getseam.com",

                    // Callbacks!
                    onInitialized = { error ->
                      System.out.println(error.toString())
                      // Authorization Errors, e.g. bad client session
                      if (error != null) {
                        runOnUiThread {
                          binding.loginErrorText?.setText(error.toString())
                          binding.loading.visibility = View.GONE
                        }
                      }
                    },
                    onCredentialStateUpdate = { credentialState ->
                      System.out.println(credentialState.error.toString())
                      System.out.println(credentialState.credentials.toString())
                      if (credentialState.credentials.count() > 0 && credentialsLoaded == false) {
                        credentialsLoaded = true;
                        val credentialCount = credentialState.credentials.count()
                        Looper.prepare()
                        Toast.makeText(applicationContext, "Got $credentialCount Credentials", Toast.LENGTH_LONG).show()
                        showEntrances()
                      }
                    },
                    onBluetoothStateChange = { status ->
                      // None
                    },
                  )
              }
            }
        }
    }


     private fun showEntrances() {
       runBlocking {
         if (seamDeviceController != null) {
           val cardReaders = seamDeviceController!!.scan()

           runOnUiThread {
             binding.loading.visibility = View.GONE
             binding.loginErrorText?.setText("")

             for (reader in cardReaders) {
               val tv = TextView(applicationContext)
               tv.setText("Entrance: " + reader.name)
               binding.myCredentialContainer?.addView(tv)
             }
             binding.unlockNearestButton?.isEnabled = true
           }
         }
       }


    }

    private fun updateUiWithUser(model: LoggedInUserView) {
        val welcome = getString(R.string.welcome)
        val displayName = model.displayName
        // TODO : initiate successful logged in experience
        Toast.makeText(
                applicationContext,
                "$welcome $displayName",
                Toast.LENGTH_LONG
        ).show()
    }

    private fun showLoginFailed(@StringRes errorString: Int) {
        Toast.makeText(applicationContext, errorString, Toast.LENGTH_SHORT).show()
    }
}

/**
 * Extension function to simplify setting an afterTextChanged action to EditText components.
 */
fun EditText.afterTextChanged(afterTextChanged: (String) -> Unit) {
    this.addTextChangedListener(object : TextWatcher {
        override fun afterTextChanged(editable: Editable?) {
            afterTextChanged.invoke(editable.toString())
        }

        override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}

        override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {}
    })
}
