package com.khaleel.newleaf.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal fun PhotoGallery(paths:List<String>) {
    paths.forEachIndexed{index,path->
        val bitmap by produceState<android.graphics.Bitmap?>(null,path) {value=withContext(Dispatchers.IO){runCatching{BitmapFactory.decodeFile(path,BitmapFactory.Options().apply{inSampleSize=4})}.getOrNull()}}
        bitmap?.let{Image(it.asImageBitmap(),"Attached photo ${index+1}",Modifier.fillMaxWidth().heightIn(min=160.dp,max=320.dp),contentScale=ContentScale.Fit)} ?: Caption("Photo ${index+1} cannot be opened")
    }
}
