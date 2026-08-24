package com.sybbox.ui.theme

import androidx.compose.ui.unit.dp

/**
 * One spacing scale for the whole app.
 *
 * Every card, row and chip used to carry its own hand-written numbers — card insets came in
 * four different combinations and chips in three — so identical-looking elements sat at
 * visibly different depths depending on which screen they were on.
 *
 * A trailing icon button carries its own touch padding, so a row that ends in one uses
 * [cardEndInset] instead of [cardH]; otherwise the content looks pushed away from the edge.
 */
object SybSpacing {

    /** Distance from the screen edge to any content. */
    val screen = 16.dp

    /** Inset from a card's own edge to its content. */
    val cardH = 16.dp
    val cardV = 14.dp

    /** Trailing inset for a row that ends in an icon button. */
    val cardEndInset = 8.dp

    /** Gap between a leading icon tile and the text beside it. */
    val rowGap = 14.dp

    /** Inset for a settings row, which is denser than a card. */
    val rowH = 16.dp
    val rowV = 12.dp

    /** Chip and badge insets. */
    val chipH = 10.dp
    val chipV = 5.dp

    /** General steps, smallest to largest. */
    val hair = 2.dp
    val tight = 4.dp
    val small = 8.dp
    val medium = 12.dp
    val large = 20.dp
    val xlarge = 24.dp

    /** Clearance under a scrolling list so a floating control never covers the last row. */
    val listBottom = 96.dp
}
