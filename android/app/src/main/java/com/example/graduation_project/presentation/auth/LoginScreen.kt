package com.example.graduation_project.presentation.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.graduation_project.ui.theme.LocalEchoColors
import com.example.graduation_project.ui.theme.Graduation_projectTheme
import com.example.graduation_project.ui.theme.OutfitFontFamily

@Composable
fun LoginScreen(
    onLoginSuccess: () -> Unit,
    onNavigateToSignup: () -> Unit,
    viewModel: LoginViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.isLoginSuccess) {
        if (uiState.isLoginSuccess) onLoginSuccess()
    }

    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.dismissError()
        }
    }

    val colors = LocalEchoColors.current
    Scaffold(
        containerColor = colors.bgPage,
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(colors.bgPage)
                .padding(padding)
                .padding(horizontal = 32.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 앱 로고
            Text(
                text = "에코",
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = OutfitFontFamily,
                color = colors.accentGreen
            )

            Spacer(Modifier.height(8.dp))

            Text(
                text = "함께 이야기 나눠요",
                fontSize = 16.sp,
                fontFamily = OutfitFontFamily,
                color = colors.textSecondary
            )

            Spacer(Modifier.height(48.dp))

            EchoOutlinedTextField(
                value = uiState.loginId,
                onValueChange = viewModel::updateLoginId,
                label = "아이디",
                keyboardType = KeyboardType.Ascii
            )

            Spacer(Modifier.height(16.dp))

            EchoOutlinedTextField(
                value = uiState.password,
                onValueChange = viewModel::updatePassword,
                label = "비밀번호",
                keyboardType = KeyboardType.Password,
                isPassword = true
            )

            Spacer(Modifier.height(32.dp))

            Button(
                onClick = viewModel::login,
                enabled = !uiState.isLoading,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.accentGreen,
                    contentColor = Color.White,
                    disabledContainerColor = colors.accentGreen.copy(alpha = 0.6f)
                )
            ) {
                if (uiState.isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.height(22.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text(
                        text = "로그인",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = OutfitFontFamily
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            TextButton(onClick = onNavigateToSignup) {
                Text(
                    text = "회원가입하기",
                    fontSize = 16.sp,
                    fontFamily = OutfitFontFamily,
                    color = colors.textSecondary
                )
            }
        }
    }
}

@Composable
internal fun EchoOutlinedTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    keyboardType: KeyboardType = KeyboardType.Text,
    isPassword: Boolean = false,
    isError: Boolean = false,
    supportingText: String? = null
) {
    val colors = LocalEchoColors.current
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = {
            Text(
                text = label,
                fontFamily = OutfitFontFamily,
                color = colors.textTertiary,
                fontSize = 16.sp
            )
        },
        singleLine = true,
        isError = isError,
        supportingText = supportingText?.let { { Text(it, fontFamily = OutfitFontFamily, fontSize = 13.sp) } },
        visualTransformation = if (isPassword) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        shape = RoundedCornerShape(12.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = colors.bgCard,
            unfocusedContainerColor = colors.bgCard,
            focusedBorderColor = colors.accentGreen,
            unfocusedBorderColor = colors.borderSubtle,
            focusedTextColor = colors.textPrimary,
            unfocusedTextColor = colors.textPrimary,
            cursorColor = colors.accentGreen
        ),
        modifier = Modifier.fillMaxWidth()
    )
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun LoginScreenPreview() {
    Graduation_projectTheme {
        val colors = LocalEchoColors.current
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(colors.bgPage)
                .padding(horizontal = 32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("에코", fontSize = 32.sp, fontWeight = FontWeight.Bold, fontFamily = OutfitFontFamily, color = colors.accentGreen)
            Spacer(Modifier.height(8.dp))
            Text("함께 이야기 나눠요", fontSize = 16.sp, fontFamily = OutfitFontFamily, color = colors.textSecondary)
            Spacer(Modifier.height(48.dp))
            EchoOutlinedTextField("", {}, "아이디")
            Spacer(Modifier.height(16.dp))
            EchoOutlinedTextField("", {}, "비밀번호", isPassword = true)
            Spacer(Modifier.height(32.dp))
            Button(
                onClick = {},
                modifier = Modifier.fillMaxWidth().height(64.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = colors.accentGreen)
            ) {
                Text("로그인", fontSize = 22.sp, fontWeight = FontWeight.SemiBold, fontFamily = OutfitFontFamily)
            }
            Spacer(Modifier.height(12.dp))
            TextButton({}) { Text("회원가입하기", fontSize = 16.sp, fontFamily = OutfitFontFamily, color = colors.textSecondary) }
        }
    }
}
