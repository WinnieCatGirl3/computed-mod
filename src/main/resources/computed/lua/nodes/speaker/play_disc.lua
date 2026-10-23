local node = computed.node(1, "computed:speaker_play_disc", "Speaker Play Disc")

node:category("speaker")
node:style("sink")
node:execution("tick")
node:field("face", "direction", { default = "front", label = "Face" })
node:field("loop", "boolean", { default = false, label = "Loop" })
node:input("trigger", "boolean", { default = false })
node:input("event", "event", { required = false, default = 0 })
node:state("loop_init", false)
node:state("loop_sent", false)
node:state("face_sent", "")
node:state("prev_trigger", false)
node:state("prev_event", 0)
node:on_run(function(ctx)
    local face = ctx:field("face")
    local loop = ctx:field("loop")
    local trigger = ctx:input("trigger")
    local event = ctx:input("event")
    local changed = trigger ~= ctx:state("prev_trigger") or event ~= ctx:state("prev_event")
    local active = trigger or event ~= 0
    ctx:set_state("prev_trigger", trigger)
    ctx:set_state("prev_event", event)
    local push = not ctx:state("loop_init")
            or loop ~= ctx:state("loop_sent")
            or face ~= ctx:state("face_sent")
    if push or (changed and active) then
        local speaker = ctx:endpoint("computed:speaker", face)
        speaker:call("set_loop", loop)
        ctx:set_state("loop_init", true)
        ctx:set_state("loop_sent", loop)
        ctx:set_state("face_sent", face)
        if changed and active then
            speaker:call("play")
        end
    end
end)

return node
