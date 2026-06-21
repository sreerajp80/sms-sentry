package `in`.sreerajp.sms_sentry.engine

import android.content.Context
import android.content.Intent
import `in`.sreerajp.sms_sentry.data.SMSMessage
import `in`.sreerajp.sms_sentry.data.SmsRepository
import org.json.JSONArray
import org.json.JSONObject

object SmsShareUtils {

    // Converts list of messages to CSV string
    fun messagesToCsv(messages: List<SMSMessage>): String {
        val s = StringBuilder()
        s.append("id,sender,body,timestamp,category,simId,isBlocked\n")
        for (m in messages) {
            val safeBody = m.body.replace("\"", "\"\"").replace("\n", " ")
            s.append("${m.id},\"${m.sender}\",\"$safeBody\",${m.timestamp},\"${m.category}\",${m.simId},${if (m.isBlocked) 1 else 0}\n")
        }
        return s.toString()
    }

    // Converts list of messages to JSON string
    fun messagesToJson(messages: List<SMSMessage>): String {
        val arr = JSONArray()
        for (m in messages) {
            val obj = JSONObject()
            obj.put("sender", m.sender)
            obj.put("body", m.body)
            obj.put("timestamp", m.timestamp)
            obj.put("category", m.category)
            obj.put("simId", m.simId)
            obj.put("isBlocked", m.isBlocked)
            arr.put(obj)
        }
        return arr.toString()
    }

    // Shares text directly using system sharing intents
    fun shareText(context: Context, title: String, text: String) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, title)
            putExtra(Intent.EXTRA_TEXT, text)
        }
        context.startActivity(Intent.createChooser(intent, "Share Data"))
    }

    // Import from CSV text
    suspend fun importFromCsv(csvText: String, repository: SmsRepository): Int {
        val lines = csvText.lineSequence().toList()
        if (lines.size <= 1) return 0
        
        var imported = 0
        // Find columns
        val header = lines[0].split(",")
        val senderIdx = header.indexOfFirst { it.trim().equals("sender", true) }
        val bodyIdx = header.indexOfFirst { it.trim().equals("body", true) }
        val simIdx = header.indexOfFirst { it.trim().equals("simId", true) }

        if (senderIdx == -1 || bodyIdx == -1) {
            // Fallback parse by indices if headers are missing
            for (i in 1 until lines.size) {
                val line = lines[i].trim()
                if (line.isEmpty()) continue
                val parts = line.split(Regex(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)")) // split by commas not inside quotes
                if (parts.size >= 3) {
                    val sender = parts[1].replace("\"", "").trim()
                    val body = parts[2].replace("\"", "").trim()
                    val simId = parts.getOrNull(5)?.toIntOrNull() ?: 1
                    repository.processAndInsertMessage(sender, body, System.currentTimeMillis(), simId)
                    imported++
                }
            }
            return imported
        }

        // Standard CSV parse with regex
        for (i in 1 until lines.size) {
            val line = lines[i].trim()
            if (line.isEmpty()) continue
            val parts = line.split(Regex(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)"))
            try {
                val sender = parts.getOrNull(senderIdx)?.replace("\"", "")?.trim() ?: continue
                val body = parts.getOrNull(bodyIdx)?.replace("\"", "")?.trim() ?: continue
                val simId = parts.getOrNull(simIdx)?.toIntOrNull() ?: 1
                repository.processAndInsertMessage(sender, body, System.currentTimeMillis(), simId)
                imported++
            } catch (e: Exception) {
                // Ignore corrupt row
            }
        }
        return imported
    }

    // Import from JSON text
    suspend fun importFromJson(jsonText: String, repository: SmsRepository): Int {
        var imported = 0
        try {
            val cleaned = jsonText.trim()
            if (cleaned.startsWith("[")) {
                val arr = JSONArray(cleaned)
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    val sender = obj.getString("sender")
                    val body = obj.getString("body")
                    val simId = obj.optInt("simId", 1)
                    val timestamp = obj.optLong("timestamp", System.currentTimeMillis())
                    repository.processAndInsertMessage(sender, body, timestamp, simId)
                    imported++
                }
            } else if (cleaned.startsWith("{")) {
                val obj = JSONObject(cleaned)
                val sender = obj.getString("sender")
                val body = obj.getString("body")
                val simId = obj.optInt("simId", 1)
                repository.processAndInsertMessage(sender, body, System.currentTimeMillis(), simId)
                imported++
            }
        } catch (e: Exception) {
            // Invalid JSON
        }
        return imported
    }
}
