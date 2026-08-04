package cn.mocabolka.run.ui.theme

import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.DrawModifierNode

/**
 * 零渲染指示器：触控模式下覆盖 MaterialTheme 默认 ripple，
 * 使所有 Material3 组件（DropdownMenuItem / Button / Tab 等）不显示任何涟漪 / 焦点高亮反馈。
 *
 * 使用现代 IndicationNodeFactory（取代已废弃的 IndicationInstance + rememberUpdatedInstance）。
 * create 返回空的 DrawModifierNode：既不绘制任何指示，也不消费任何交互。
 */
object NoIndication : IndicationNodeFactory {
    override fun create(interactionSource: InteractionSource): DelegatableNode {
        return object : Modifier.Node(), DrawModifierNode {
            override fun ContentDrawScope.draw() {
                drawContent()
            }
        }
    }

    override fun hashCode(): Int = 0

    override fun equals(other: Any?): Boolean = other === this
}
