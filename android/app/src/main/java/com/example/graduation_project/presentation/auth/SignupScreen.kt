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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.graduation_project.ui.theme.EchoAccentGreen
import com.example.graduation_project.ui.theme.EchoBgPage
import com.example.graduation_project.ui.theme.EchoTextSecondary
import com.example.graduation_project.ui.theme.Graduation_projectTheme
import com.example.graduation_project.ui.theme.OutfitFontFamily

@Composable
fun SignupScreen(
    onSignupSuccess: () -> Unit,
    viewModel: SignupViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.isSignupSuccess) {
        if (uiState.isSignupSuccess) onSignupSuccess()
    }

    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.dismissError()
        }
    }

    Scaffold(
        containerColor = EchoBgPage,
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(EchoBgPage)
                .padding(padding)
                .padding(horizontal = 32.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "에코",
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = OutfitFontFamily,
                color = EchoAccentGreen
            )

            Spacer(Modifier.height(8.dp))

            Text(
                text = "계정을 만들어보세요",
                fontSize = 16.sp,
                fontFamily = OutfitFontFamily,
                color = EchoTextSecondary
            )

            Spacer(Modifier.height(40.dp))

            EchoOutlinedTextField(
                value = uiState.name,
                onValueChange = viewModel::updateName,
                label = "이름",
                isError = uiState.nameError != null,
                supportingText = uiState.nameError ?: "최대 50자"
            )

            Spacer(Modifier.height(12.dp))

            EchoOutlinedTextField(
                value = uiState.loginId,
                onValueChange = viewModel::updateLoginId,
                label = "아이디",
                keyboardType = KeyboardType.Ascii,
                isError = uiState.loginIdError != null,
                supportingText = uiState.loginIdError ?: "영문/숫자 4~20자"
            )

            Spacer(Modifier.height(12.dp))

            EchoOutlinedTextField(
                value = uiState.password,
                onValueChange = viewModel::updatePassword,
                label = "비밀번호",
                keyboardType = KeyboardType.Password,
                isPassword = true,
                isError = uiState.passwordError != null,
                supportingText = uiState.passwordError ?: "8자 이상, 영문/숫자 조합"
            )

            Spacer(Modifier.height(32.dp))

            Button(
                onClick = viewModel::signup,
                enabled = !uiState.isLoading,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = EchoAccentGreen,
                    contentColor = Color.White,
                    disabledContainerColor = EchoAccentGreen.copy(alpha = 0.6f)
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
                        text = "가입하기",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = OutfitFontFamily
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun SignupScreenPreview() {
    Graduation_projectTheme {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(EchoBgPage)
                .padding(horizontal = 32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("에코", fontSize = 32.sp, fontWeight = FontWeight.Bold, fontFamily = OutfitFontFamily, color = EchoAccentGreen)
            Spacer(Modifier.height(8.dp))
            Text("계정을 만들어보세요", fontSize = 16.sp, fontFamily = OutfitFontFamily, color = EchoTextSecondary)
            Spacer(Modifier.height(40.dp))
            EchoOutlinedTextField("", {}, "이름")
            Spacer(Modifier.height(12.dp))
            EchoOutlinedTextField("", {}, "아이디")
            Spacer(Modifier.height(12.dp))
            EchoOutlinedTextField("", {}, "비밀번호", isPassword = true)
            Spacer(Modifier.height(32.dp))
            Button(
                onClick = {},
                modifier = Modifier.fillMaxWidth().height(64.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = EchoAccentGreen)
            ) {
                Text("가입하기", fontSize = 22.sp, fontWeight = FontWeight.SemiBold, fontFamily = OutfitFontFamily)
            }
        }
    }
}
