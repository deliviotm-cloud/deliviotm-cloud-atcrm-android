package tm.deliviotm.atcrm

import android.content.Context
import android.print.PrintAttributes
import android.print.PrintManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicReference

private val printWebView = AtomicReference<WebView?>(null)

internal fun printReportHtml(context: Context, title: String, html: String) {
    val web = WebView(context.applicationContext)
    printWebView.set(web)
    web.webViewClient = object : WebViewClient() {
        override fun onPageFinished(view: WebView?, url: String?) {
            val printer = context.getSystemService(Context.PRINT_SERVICE) as? PrintManager ?: return
            printer.print(
                title,
                view!!.createPrintDocumentAdapter(title),
                PrintAttributes.Builder()
                    .setMediaSize(PrintAttributes.MediaSize.ISO_A4)
                    .setMinMargins(PrintAttributes.Margins.NO_MARGINS)
                    .build(),
            )
        }
    }
    web.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
}

internal fun reportsPrintHtml(
    tab: String,
    rangeLabel: String,
    summaryKpis: List<Pair<String, String>> = emptyList(),
    tables: List<Pair<String, List<List<String>>>> = emptyList(),
): String {
    val body = StringBuilder()
    body.append("<h1>Отчёты — ${escape(tabTitle(tab))}</h1>")
    body.append("<p>Период ${escape(rangeLabel)}</p>")
    if (summaryKpis.isNotEmpty()) {
        body.append("<table>")
        summaryKpis.forEach { (k, v) ->
            body.append("<tr><td>${escape(k)}</td><td><b>${escape(v)}</b></td></tr>")
        }
        body.append("</table>")
    }
    tables.forEach { (title, rows) ->
        body.append("<h2>${escape(title)}</h2><table border='1' cellpadding='6' cellspacing='0'>")
        rows.forEachIndexed { i, cols ->
            val tag = if (i == 0) "th" else "td"
            body.append("<tr>")
            cols.forEach { c -> body.append("<$tag>${escape(c)}</$tag>") }
            body.append("</tr>")
        }
        body.append("</table>")
    }
    return """
        <html><head><meta charset="utf-8"><style>
        body{font-family:sans-serif;font-size:12px;color:#111;padding:16px}
        table{border-collapse:collapse;width:100%;margin:12px 0}
        th{text-align:left;background:#eef2ff}
        h1{font-size:20px} h2{font-size:16px;margin-top:18px}
        </style></head><body>$body</body></html>
    """.trimIndent()
}

private fun tabTitle(tab: String) = when (tab) {
    "orders" -> "Заказы"
    "cancelled" -> "Отменённые"
    "compare" -> "Сравнение"
    "salesCrm" -> "CRM продаж"
    "profitFinance" -> "Фин. отчёт"
    else -> "Сводка"
}

private fun escape(s: String) = s
    .replace("&", "&amp;")
    .replace("<", "&lt;")
    .replace(">", "&gt;")

@Composable
internal fun ReportOrderRow(o: JSONObject) {
    val number = KassaApi.pick(o, "orderNumber", "externalId", "id").ifBlank { "—" }
    val whenAt = KassaApi.ruDateTime(KassaApi.pick(o, "orderDatetime", "datetime", "createdAt"))
    val city = KassaApi.cityLabel(KassaApi.pick(o, "cityKey", "city"))
    val est = KassaApi.nestedEstablishmentName(o).ifBlank { "—" }
    val amount = KassaApi.tmt(KassaApi.jsonNum(o, "orderAmount", "amount", "turnover"))
    val pay = KassaApi.payLabel(KassaApi.pick(o, "paymentType", "payment"))
    Column(Modifier.fillMaxWidth().atCard(14.dp).padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("№ $number", color = AtColors.text, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Text(pay, color = AtColors.muted, fontSize = 12.sp)
        }
        Text(whenAt, color = AtColors.text, fontSize = 13.sp)
        Text("$city · $est", color = AtColors.muted, fontSize = 13.sp)
        Text(amount, color = AtColors.text, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
    }
}

@Composable
internal fun ReportCancelledRow(o: JSONObject) {
    val number = KassaApi.pick(o, "orderNumber", "externalId", "id").ifBlank { "—" }
    val whenAt = KassaApi.ruDateTime(KassaApi.pick(o, "orderDatetime", "datetime"))
    val city = KassaApi.cityLabel(KassaApi.pick(o, "cityKey", "city"))
    val est = KassaApi.nestedEstablishmentName(o).ifBlank { "—" }
    val type = KassaApi.nestedEstablishmentType(o).ifBlank { "—" }
    val user = KassaApi.pick(o, "appClientExternalUserId", "userId", "clientId").ifBlank { "—" }
    val amount = KassaApi.tmt(KassaApi.jsonNum(o, "orderAmount", "amount"))
    val delivery = KassaApi.tmt(KassaApi.jsonNum(o, "deliveryAmount", "delivery"))
    val pay = KassaApi.payLabel(KassaApi.pick(o, "paymentType"))
    val reason = KassaApi.pick(o, "cancelReason", "reason", "comment").ifBlank { "—" }
    val cancelledAt = KassaApi.pick(o, "cancelledAt", "canceledAt").let {
        if (it.isBlank()) "—" else KassaApi.ruDateTime(it)
    }
    Column(Modifier.fillMaxWidth().atCard(14.dp).padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text("№ $number", color = AtColors.text, fontWeight = FontWeight.Bold, fontSize = 15.sp)
        MetaLine("Дата заказа", whenAt)
        MetaLine("Город", city)
        MetaLine("Заведение", est)
        MetaLine("Тип", type)
        MetaLine("ID пользователя", user)
        MetaLine("Сумма", amount)
        MetaLine("Доставка", delivery)
        MetaLine("Оплата", pay)
        MetaLine("Причина отмены", reason)
        MetaLine("Отменён", cancelledAt)
    }
}

@Composable
internal fun ReportCrmItemRow(o: JSONObject) {
    val kind = KassaApi.pick(o, "kind", "type")
    val kindLabel = when (kind.uppercase()) {
        "SHOP", "STORE" -> "Магазины"
        "RESTAURANT" -> "Рестораны"
        else -> kind.ifBlank { "—" }
    }
    val at = KassaApi.pick(o, "at", "connectedAt", "date")
    Column(Modifier.fillMaxWidth().atCard(14.dp).padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(KassaApi.pick(o, "title", "name").ifBlank { "—" }, color = AtColors.text, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
        MetaLine("ID заведения", KassaApi.pick(o, "delivioId", "id").ifBlank { "—" })
        MetaLine("Тип заведения", kindLabel)
        MetaLine("Период", if (at.isBlank()) "—" else KassaApi.ruDateTime(at, withSeconds = false))
    }
}

@Composable
internal fun ReportExpenseRow(o: JSONObject) {
    Column(Modifier.fillMaxWidth().atCard(14.dp).padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(KassaApi.expenseKindLabel(KassaApi.pick(o, "kind")), color = AtColors.text, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
        MetaLine("№", KassaApi.pick(o, "orderNumber").ifBlank { "—" })
        MetaLine(
            "Дата заказа",
            KassaApi.pick(o, "orderDatetime").let { if (it.isBlank()) "—" else KassaApi.ruDateTime(it, withSeconds = false) },
        )
        MetaLine("Город", KassaApi.cityLabel(KassaApi.pick(o, "cityKey")))
        MetaLine("Заведение", KassaApi.pick(o, "establishmentName").ifBlank { KassaApi.nestedEstablishmentName(o) }.ifBlank { "—" })
        MetaLine("Примечание", KassaApi.pick(o, "note").ifBlank { "—" })
        MetaLine("Сумма", KassaApi.tmt(KassaApi.jsonNum(o, "amount")))
    }
}

@Composable
private fun MetaLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, color = AtColors.muted, fontSize = 12.sp, modifier = Modifier.weight(0.42f))
        Text(value, color = AtColors.text, fontSize = 13.sp, modifier = Modifier.weight(0.58f))
    }
}

@Composable
internal fun ReportHint(text: String) {
    Text(text, color = AtColors.muted, fontSize = 13.sp)
}

internal fun crmKindLabel(kind: String): String = when (kind.uppercase()) {
    "SHOP", "STORE" -> "Магазины"
    "RESTAURANT" -> "Рестораны"
    else -> kind.ifBlank { "—" }
}
