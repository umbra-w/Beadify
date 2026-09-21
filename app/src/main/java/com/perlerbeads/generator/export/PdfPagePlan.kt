package com.perlerbeads.generator.export

/**
 * PDF 分页计划（纯函数，无 Android 依赖，便于单测）。
 * 一页承载 colsPerPage×rowsPerPage 格，边缘页按网格实际尺寸收窄。
 */
data class PdfTile(
    val index: Int,     // 行优先序号
    val pageCol: Int,   // 横向第几页
    val pageRow: Int,   // 纵向第几页
    val colStart: Int,
    val rowStart: Int,
    val cols: Int,
    val rows: Int
)

private fun ceilDiv(a: Int, b: Int): Int = (a + b - 1) / b

/** 把 n×m 网格分页；页容量必须为正。 */
fun planPdfTiles(n: Int, m: Int, colsPerPage: Int, rowsPerPage: Int): List<PdfTile> {
    require(colsPerPage > 0 && rowsPerPage > 0) { "page capacity must be positive" }
    if (n <= 0 || m <= 0) return emptyList()
    val pageCols = ceilDiv(n, colsPerPage)
    val pageRows = ceilDiv(m, rowsPerPage)
    val result = ArrayList<PdfTile>(pageCols * pageRows)
    for (pr in 0 until pageRows) {
        for (pc in 0 until pageCols) {
            val colStart = pc * colsPerPage
            val rowStart = pr * rowsPerPage
            result.add(
                PdfTile(
                    index = pr * pageCols + pc,
                    pageCol = pc,
                    pageRow = pr,
                    colStart = colStart,
                    rowStart = rowStart,
                    cols = minOf(colsPerPage, n - colStart),
                    rows = minOf(rowsPerPage, m - rowStart)
                )
            )
        }
    }
    return result
}
