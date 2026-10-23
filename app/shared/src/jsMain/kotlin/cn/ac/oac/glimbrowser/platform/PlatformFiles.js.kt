package cn.ac.oac.glimbrowser.platform

actual fun saveTextFile(fileName: String, content: String): String? {
    downloadText(fileName, content)
    return "浏览器下载"
}

private fun downloadText(fileName: String, content: String) {
    js(
        """
        (function(){
          var blob = new Blob([content], {type: 'text/html;charset=utf-8'});
          var a = document.createElement('a');
          a.href = URL.createObjectURL(blob);
          a.download = fileName;
          document.body.appendChild(a); a.click(); document.body.removeChild(a);
        })()
        """
    )
}
