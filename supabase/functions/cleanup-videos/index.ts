import { createClient } from 'https://esm.sh/@supabase/supabase-js@2'

Deno.serve(async (req) => {
  const supabase = createClient(
    Deno.env.get('SUPABASE_URL') ?? '',
    Deno.env.get('SUPABASE_SERVICE_ROLE_KEY') ?? ''
  )

  try {
    // 1. 获取 50 个最久未检查的活跃视频
    const { data: videos, error: fetchError } = await supabase
      .from('videos')
      .select('id, source_url')
      .eq('is_active', true)
      .order('created_at', { ascending: true })
      .limit(50)

    if (fetchError) throw fetchError

    const results = { checked: 0, deactivated: 0, errors: 0 }

    // 2. 并发检查 URL 状态
    await Promise.all(videos.map(async (video) => {
      try {
        results.checked++
        const response = await fetch(video.source_url, { 
          method: 'HEAD',
          headers: { 'User-Agent': 'Mozilla/5.0' } 
        })

        if (response.status === 404) {
          // 如果 404，标记为非活跃
          await supabase
            .from('videos')
            .update({ is_active: false })
            .eq('id', video.id)
          results.deactivated++
        }
      } catch (e) {
        results.errors++
        console.error(`Failed to check ${video.source_url}:`, e)
      }
    }))

    return new Response(JSON.stringify({ message: "Cleanup completed", ...results }), {
      headers: { "Content-Type": "application/json" },
    })

  } catch (error) {
    return new Response(JSON.stringify({ error: error.message }), {
      status: 500,
      headers: { "Content-Type": "application/json" },
    })
  }
})
