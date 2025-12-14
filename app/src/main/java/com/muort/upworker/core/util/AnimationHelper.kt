package com.muort.upworker.core.util

import android.view.View
import android.view.animation.AnimationUtils
import com.muort.upworker.R

object AnimationHelper {
    
    fun fadeInUp(view: View) {
        val animation = AnimationUtils.loadAnimation(view.context, R.anim.fade_in_up)
        view.startAnimation(animation)
    }
    
    fun fadeOutDown(view: View) {
        val animation = AnimationUtils.loadAnimation(view.context, R.anim.fade_out_down)
        view.startAnimation(animation)
    }
    
    fun scaleDown(view: View) {
        val animation = AnimationUtils.loadAnimation(view.context, R.anim.scale_down)
        view.startAnimation(animation)
    }
    
    fun scaleUp(view: View) {
        val animation = AnimationUtils.loadAnimation(view.context, R.anim.scale_up)
        view.startAnimation(animation)
    }
}
