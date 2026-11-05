package com.adnan.memora.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.adnan.memora.R
import java.text.NumberFormat

/** Compact numeric badge, full count announced to accessibility; action targets remain 48dp. */
@Composable
internal fun SelectionCount(count:Int,modifier:Modifier=Modifier) {
    val description=stringResource(R.string.selected_count,count)
    Text(NumberFormat.getIntegerInstance().format(count),
        modifier.background(MaterialTheme.colorScheme.secondaryContainer,RoundedCornerShape(10.dp))
            .padding(horizontal=4.dp,vertical=5.dp).semantics { contentDescription=description },
        style=MaterialTheme.typography.labelLarge,color=MaterialTheme.colorScheme.onSecondaryContainer,
        textAlign=TextAlign.Center,maxLines=1,overflow=TextOverflow.Ellipsis)
}
