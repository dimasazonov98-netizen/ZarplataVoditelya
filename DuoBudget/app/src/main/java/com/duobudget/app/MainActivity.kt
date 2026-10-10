package com.duobudget.app
import android.app.Activity
import android.app.KeyguardManager
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.duobudget.app.ui.DuoBudgetApp
import com.duobudget.app.ui.theme.DuoBudgetTheme
import com.duobudget.app.ui.theme.ThemePreferences

class MainActivity:AppCompatActivity() {
    private val unlocked=mutableStateOf(false)
    private val hasUnlocked=mutableStateOf(false)
    private val expenseRequest=mutableStateOf(0)
    private var promptShowing=false
    private var credentialShowing=false
    private var attempted=false
    private var biometric=false
    private var secureDevice=false
    private val keyguard by lazy{getSystemService(KEYGUARD_SERVICE) as KeyguardManager}
    private val confirmCredential=registerForActivityResult(ActivityResultContracts.StartActivityForResult()){
        credentialShowing=false
        if(it.resultCode==Activity.RESULT_OK)unlock()
    }
    override fun onCreate(savedInstanceState:Bundle?){
        ThemePreferences.init(this)
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        biometric=BiometricManager.from(this).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.BIOMETRIC_WEAK)==BiometricManager.BIOMETRIC_SUCCESS
        secureDevice=keyguard.isDeviceSecure
        if(!biometric&&!secureDevice)unlock()
        if(intent?.getBooleanExtra("openAddExpense",false)==true){expenseRequest.value++;intent.removeExtra("openAddExpense")}
        setContent{
            DuoBudgetTheme{
                Box(Modifier.fillMaxSize()){
                    // Keep drafts and document-picker callbacks alive when the app locks.
                    if(hasUnlocked.value){
                        val privacy=if(unlocked.value)Modifier else Modifier.alpha(0f).clearAndSetSemantics{}.pointerInput(Unit){
                            awaitPointerEventScope{while(true){awaitPointerEvent(PointerEventPass.Initial).changes.forEach{it.consume()}}}
                        }
                        Box(Modifier.fillMaxSize().then(privacy)){DuoBudgetApp(expenseRequest.value,unlocked.value)}
                    }
                    if(!unlocked.value){
                        Surface(Modifier.fillMaxSize()){
                            Column(Modifier.fillMaxSize().padding(24.dp),verticalArrangement=Arrangement.Center,horizontalAlignment=Alignment.CenterHorizontally){
                                Text("DuoBudget",style=MaterialTheme.typography.headlineMedium,fontWeight=FontWeight.Bold)
                                Text("Подтвердите вход отпечатком, лицом или кодом блокировки.",Modifier.padding(top=12.dp,bottom=24.dp))
                                Button(onClick=::showPrompt){Text("Разблокировать")}
                                if(secureDevice&&Build.VERSION.SDK_INT<30)TextButton(onClick=::showCredential){Text("Использовать код блокировки")}
                            }
                        }
                        BackHandler{finish()}
                    }
                }
            }
        }
    }
    override fun onNewIntent(intent:Intent){super.onNewIntent(intent);setIntent(intent);if(intent.getBooleanExtra("openAddExpense",false)){expenseRequest.value++;intent.removeExtra("openAddExpense")}}
    override fun onResume(){super.onResume();if(!unlocked.value&&!attempted){attempted=true;showPrompt()}}
    override fun onStop(){
        super.onStop()
        if(!isChangingConfigurations&&(biometric||secureDevice)){
            unlocked.value=false
            if(!credentialShowing)attempted=false
        }
    }
    private fun unlock(){unlocked.value=true;hasUnlocked.value=true;promptShowing=false;attempted=true}
    private fun showCredential(){
        if(credentialShowing)return
        val intent=keyguard.createConfirmDeviceCredentialIntent("Вход в DuoBudget","Введите код блокировки устройства")?:return
        credentialShowing=true;attempted=true;confirmCredential.launch(intent)
    }
    private fun showPrompt(){
        if(unlocked.value||promptShowing||credentialShowing)return
        attempted=true
        if(!biometric){if(secureDevice)showCredential()else unlock();return}
        promptShowing=true
        val prompt=BiometricPrompt(this,ContextCompat.getMainExecutor(this),object:BiometricPrompt.AuthenticationCallback(){
            override fun onAuthenticationSucceeded(result:BiometricPrompt.AuthenticationResult){unlock()}
            override fun onAuthenticationError(errorCode:Int,errString:CharSequence){
                promptShowing=false
                if(errorCode==BiometricPrompt.ERROR_NEGATIVE_BUTTON&&secureDevice)showCredential()
            }
        })
        var allowed=BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.BIOMETRIC_WEAK
        val builder=BiometricPrompt.PromptInfo.Builder().setTitle("Вход в DuoBudget").setSubtitle("Подтвердите личность")
        if(Build.VERSION.SDK_INT>=30&&secureDevice)allowed=allowed or BiometricManager.Authenticators.DEVICE_CREDENTIAL
        else builder.setNegativeButtonText(if(secureDevice)"Код блокировки"else"Отмена")
        prompt.authenticate(builder.setAllowedAuthenticators(allowed).build())
    }
}
