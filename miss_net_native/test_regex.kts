val html1 = "source: 'https://surfer.missav.com/playlist.m3u8'"
val html2 = "source: 'https:\\/\\/surfer.missav.com\\/playlist.m3u8'"

val m3u8Regex = Regex("""https?://[^\s"'<>]+?\.m3u8[^\s"'<>]*""")
println(m3u8Regex.find(html1)?.value)
println(m3u8Regex.find(html2)?.value)

val betterRegex = Regex("""https?:[\\/]+[^\s"'<>]+?\.m3u8[^\s"'<>]*""")
println(betterRegex.find(html1)?.value)
println(betterRegex.find(html2)?.value)
