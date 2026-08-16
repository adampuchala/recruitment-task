package com.adampuchala.mobileapp

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import com.adampuchala.mobileapp.generated.resources.Res
import com.adampuchala.mobileapp.generated.resources.document_error_no_data
import com.adampuchala.mobileapp.utils.ErrorLoadingContent
import com.adampuchala.mobileapp.utils.ErrorLoadingState
import com.adampuchala.mobileapp.ui.components.HorizontalDivider
import com.adampuchala.mobileapp.ui.components.MainHeaderTitleBar
import com.adampuchala.mobileapp.ui.components.MarkdownView
import com.adampuchala.mobileapp.ui.components.Text
import com.adampuchala.mobileapp.ui.components.TopMenuButton
import com.adampuchala.mobileapp.ui.generated.resources.UiRes
import com.adampuchala.mobileapp.ui.generated.resources.arrow_left_24
import com.adampuchala.mobileapp.ui.generated.resources.main_header_back
import com.adampuchala.mobileapp.ui.theme.MobileAppTheme
import com.adampuchala.mobileapp.utils.bottomInsetPadding
import com.adampuchala.mobileapp.utils.topInsetPadding

@Composable
fun ScreenWithTitle(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    contentScrollState: ScrollState = rememberScrollState(),
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier
            .fillMaxSize()
            .background(color = MobileAppTheme.colors.mainBackground)
            .padding(topInsetPadding())
    ) {
        MainHeaderTitleBar(
            title = title,
            startContent = {
                TopMenuButton(
                    icon = UiRes.drawable.arrow_left_24,
                    contentDescription = stringResource(UiRes.string.main_header_back),
                    onClick = onBack,
                )
            }
        )

        HorizontalDivider(thickness = 1.dp, color = MobileAppTheme.colors.strokePale)

        Column(
            Modifier
                .fillMaxSize()
                .background(color = MobileAppTheme.colors.mainBackground)
                .padding(horizontal = 12.dp)
                .verticalScroll(contentScrollState)
                .padding(bottomInsetPadding())
        ) {
            content()
        }
    }
}

@Composable
fun MarkdownScreenWithTitle(
    title: String,
    header: String,
    documentState: ErrorLoadingState<String>,
    onBack: () -> Unit,
    onReload: () -> Unit,
    onCustomUriClick: (String) -> Unit = {},
    endContent: @Composable ColumnScope.() -> Unit = {},
) {
    val scrollState = rememberScrollState()
    ScrollToTopHandler(scrollState)
    ScreenWithTitle(title, onBack, contentScrollState = scrollState) {
        if (header.isNotEmpty()) {
            Text(
                header,
                style = MobileAppTheme.typography.h1,
                modifier = Modifier.padding(top = 24.dp, bottom = 12.dp)
            )
        }

        ErrorLoadingContent(
            state = documentState,
            errorMessage = stringResource(Res.string.document_error_no_data),
            onRetry = onReload,
            modifier = Modifier.fillMaxSize(),
        ) { text ->
            Column {
                MarkdownView(
                    text = text,
                    modifier = Modifier.padding(vertical = 12.dp),
                    onCustomUriClick = onCustomUriClick,
                )
                endContent()
            }
        }
    }
}
