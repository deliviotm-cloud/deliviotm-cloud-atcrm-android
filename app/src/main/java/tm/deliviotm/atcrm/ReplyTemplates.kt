package tm.deliviotm.atcrm

import org.json.JSONObject

object ReplyTemplates {
    private val defaults = listOf(
        "Приняли, проверяем",
        "Уточню и вернусь",
        "Перезвоню через 5 минут",
        "Заказ в работе",
        "Спасибо, всё решили",
    )

    fun forContext(obj: JSONObject?, title: String, path: String = ""): List<String> {
        val blob = buildString {
            append(title)
            append(' ')
            append(path)
            if (obj != null) {
                append(' ')
                append(
                    KassaApi.pick(
                        obj,
                        "status", "state", "kind", "type", "topic", "subject",
                        "channel", "lastMessage", "problem", "comment",
                    ),
                )
            }
        }.lowercase()
        return when {
            matches(blob, "problem", "проблем", "fail", "error", "ошиб", "cancel", "отмен") -> listOf(
                "Извините за неудобство, уже разбираемся",
                "Зафиксировали проблему, вернусь с решением",
                "Могу предложить замену или возврат — что удобнее?",
                "Перезвоню через 5 минут",
            )
            matches(blob, "deliver", "достав", "courier", "курьер", "shipping", "логист") -> listOf(
                "Курьер уже в пути",
                "Подскажите, когда будете на адресе?",
                "Заказ передан в доставку",
                "Уточните подъезд и домофон, пожалуйста",
            )
            matches(blob, "support", "ticket", "обращен", "эскал") || path.contains("support") -> listOf(
                "Приняли обращение, проверяем",
                "Уточните номер заказа, пожалуйста",
                "Передам в нужный отдел",
                "Спасибо за ожидание",
            )
            matches(blob, "new", "pending", "wait", "создан", "нов", "ждет", "принят") -> listOf(
                "Заказ приняли, готовим",
                "Подтвердите адрес и время, пожалуйста",
                "Есть ли комментарий к заказу?",
                "Сообщу, как передадим курьеру",
            )
            else -> defaults
        }
    }

    private fun matches(blob: String, vararg keys: String): Boolean = keys.any { blob.contains(it) }
}
