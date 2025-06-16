// Add this new class to handle card animations
package com.qoneqo.solitaire

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView

class CardAnimationHelper {

    companion object {
        private const val ANIMATION_DURATION = 300L

        fun animateCardMove(
            sourceView: View,
            targetContainer: ViewGroup,
            cardView: ImageView,
            onAnimationEnd: () -> Unit
        ) {
            // Get source and target positions
            val sourceLocation = IntArray(2)
            val targetLocation = IntArray(2)

            sourceView.getLocationOnScreen(sourceLocation)
            targetContainer.getLocationOnScreen(targetLocation)

            // Create a temporary card view for animation
            val animatingCard = ImageView(sourceView.context).apply {
                setImageDrawable(cardView.drawable)
                scaleType = cardView.scaleType
                layoutParams = ViewGroup.LayoutParams(
                    sourceView.width,
                    sourceView.height
                )
            }

            // Add to root view
            val rootView = sourceView.rootView as ViewGroup
            rootView.addView(animatingCard)

            // Set initial position
            animatingCard.x = sourceLocation[0].toFloat()
            animatingCard.y = sourceLocation[1].toFloat()

            // Calculate target position
            val targetX = targetLocation[0].toFloat()
            val targetY = targetLocation[1].toFloat()

            // Create animation
            val moveX = ObjectAnimator.ofFloat(animatingCard, "x", targetX)
            val moveY = ObjectAnimator.ofFloat(animatingCard, "y", targetY)

            val animatorSet = AnimatorSet().apply {
                playTogether(moveX, moveY)
                duration = ANIMATION_DURATION
                addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        rootView.removeView(animatingCard)
                        onAnimationEnd()
                    }
                })
            }

            animatorSet.start()
        }

        fun animateCardFlip(cardView: ImageView, newDrawable: Int, onAnimationEnd: () -> Unit = {}) {
            val scaleX = ObjectAnimator.ofFloat(cardView, "scaleX", 1f, 0f, 1f)
            scaleX.duration = ANIMATION_DURATION
            scaleX.addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    cardView.setImageResource(newDrawable)
                    onAnimationEnd()
                }
            })
            scaleX.start()
        }
    }
}