const str = "source: 'https:\\/\\/surfer.missav.com\\/playlist.m3u8'";
const regex1 = /https?:\/\/[^\s"'<>]+?\.m3u8[^\s"'<>]*/;
console.log("regex1:", str.match(regex1)?.[0]);

const regex2 = /https?:\\?\/\\?\/[^\s"'<>]+?\.m3u8[^\s"'<>]*/;
console.log("regex2:", str.match(regex2)?.[0]);
