package com.ryo.androidfilemanager.core.domain

/** ダウンロード・アップロード完了時の要約。UI の完了メッセージ生成に使う。 */
data class TransferSummary(val fileCount: Int, val destinationPath: String)
