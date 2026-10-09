package com.duobudget.app.cloud
import com.duobudget.app.BuildConfig
import com.duobudget.app.data.StateCodec
import com.duobudget.app.model.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL

class BudgetCloudService {
    private val base=BuildConfig.CLOUD_URL.trimEnd('/')
    class CloudException(val status:Int,message:String):Exception(message)
    fun request(path:String,token:String?=null,body:JSONObject?=null,method:String=if(body==null)"GET" else "POST"):JSONObject {
        require(base.startsWith("https://"))
        val connection=URL(base+path).openConnection() as HttpURLConnection
        try {
            connection.requestMethod=method;connection.connectTimeout=8000;connection.readTimeout=10000;connection.instanceFollowRedirects=false
            connection.setRequestProperty("Accept","application/json")
            if(token!=null)connection.setRequestProperty("Authorization","Bearer "+token)
            if(body!=null){
                connection.doOutput=true;connection.setRequestProperty("Content-Type","application/json; charset=utf-8")
                val bytes=body.toString().toByteArray(Charsets.UTF_8);connection.setFixedLengthStreamingMode(bytes.size)
                connection.outputStream.use{it.write(bytes)}
            }
            val status=connection.responseCode
            val input=if(status in 200..299)connection.inputStream else connection.errorStream
            val output=ByteArrayOutputStream()
            input?.use { stream->val buffer=ByteArray(8192);while(true){val n=stream.read(buffer);if(n<0)break;require(output.size()+n<=6*1024*1024){"Слишком большой ответ сервера"};output.write(buffer,0,n)} }
            val json=runCatching{JSONObject(output.toString("UTF-8"))}.getOrNull()
            if(status !in 200..299)throw CloudException(status,json?.optString("error")?.ifBlank{null}?:"Синхронизация пока недоступна. Данные сохранены на телефоне.")
            return json?:throw IllegalStateException("Не удалось прочитать ответ сервиса")
        } finally {connection.disconnect()}
    }
    fun create(actor:String,token:String,data:BudgetData)=request("/api/create",body=JSONObject().put("actorId",actor).put("token",token).put("state",StateCodec.data(data)))
    fun join(actor:String,token:String,code:String,data:BudgetData)=request("/api/join",body=JSONObject().put("actorId",actor).put("token",token).put("code",code.trim().uppercase(java.util.Locale.ROOT)).put("state",StateCodec.data(data)))
    fun sync(session:CloudSession,commands:List<BudgetCommand>,revision:Long)=if(commands.isEmpty())request("/api/state?revision="+revision,session.token) else request("/api/sync",session.token,JSONObject().put("commands",JSONArray(commands.map(StateCodec::command))))
    fun invite(session:CloudSession)=request("/api/invite",session.token,JSONObject())
    fun disconnect(session:CloudSession)=request(if(session.role==Payer.ME)"/api/family" else "/api/member",session.token,method="DELETE")
}

