package com.beff.app.ui

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController

/**
 * 입력창 바깥을 누르면 키보드를 내린다 (1113).
 * - 입력창은 [imeField]로 자기 자리를 알린다.
 * - 앱 맨 바깥의 [dismissImeOnTapOutside]가 터치를 가로채지 않고 지켜보다가, 입력창이 아닌 곳을 눌렀다 떼면 포커스를 풀고 키보드를 내린다.
 *   바깥의 버튼은 그대로 눌린다. 끌기(스크롤)는 다른 쪽이 터치를 가져가므로 내리지 않는다.
 */
private val fields = mutableStateMapOf<Any, Rect>()

/** 이 입력창의 자리를 알린다. 이 안을 누르면 키보드를 내리지 않는다. */
fun Modifier.imeField(): Modifier = composed {
    val key = remember { Any() }
    DisposableEffect(key) { onDispose { fields.remove(key) } }
    onGloballyPositioned { fields[key] = it.boundsInRoot() }
}

/** 앱 맨 바깥(루트)에 한 번 붙인다. */
fun Modifier.dismissImeOnTapOutside(): Modifier = composed {
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    pointerInput(Unit) {
        awaitEachGesture {
            // Initial 단계에서 보기만 한다(소비하지 않음) → 자식 버튼 · 입력창은 평소대로 터치를 받는다.
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            waitForUpOrCancellation(PointerEventPass.Initial) ?: return@awaitEachGesture
            if (fields.values.none { it.contains(down.position) }) {
                focusManager.clearFocus()
                keyboard?.hide()
            }
        }
    }
}
