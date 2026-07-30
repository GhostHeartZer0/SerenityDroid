package com.example.serenity_md_viewer

import android.os.Bundle
import android.webkit.WebView
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import com.example.serenitydroid.R

class MdViewerActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_md_viewer)

        val webView = findViewById<WebView>(R.id.webView)
        webView.settings.javaScriptEnabled = true

        // Read .md from opening intent or fallback demo
        val mdText = intent?.data?.let { uri ->
            contentResolver.openInputStream(uri)?.use { stream ->
                BufferedReader(InputStreamReader(stream)).readText()
            }
        } ?: "# Serenity MD Viewer\n\n- Quick access\n- Optimized view\n- **Part of Serenity Core**"

        val html = """
            <!DOCTYPE html>
            <html>
            <head>
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <script src="https://cdn.jsdelivr.net/npm/marked/marked.min.js"></script>
                <style>
                    body { font-family: sans-serif; padding: 20px; background: #0D0D12; color: #FFFFFF; line-height: 1.6; }
                    code { background: #1A1A24; padding: 2px 6px; border-radius: 4px; color: #00FF96; }
                    pre { background: #1A1A24; padding: 12px; border-radius: 6px; overflow-x: auto; border: 1px solid #333; }
                    blockquote { border-left: 4px solid #00FF96; margin-left: 0; padding-left: 12px; color: #B0B0B0; }
                    a { color: #00FF96; }
                    h1, h2, h3 { color: #00FF96; border-bottom: 1px solid #333; padding-bottom: 8px; }
                </style>
            </head>
            <body>
                <div id="content"></div>
                <script>
                    const rawMd = ${JSONObject.quote(mdText)};
                    document.getElementById('content').innerHTML = marked.parse(rawMd);
                </script>
            </body>
            </html>
        """.trimIndent()

        webView.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
    }
}
