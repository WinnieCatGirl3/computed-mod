local node = computed.node(1, "computed:speaker_play_disc", "Speaker Play Disc")

node:category("speaker")
node:style("sink")
node:execution("input")
node:field("face", "direction", { default = "front", label = "Face" })
node:field("loop", "boolean", { default = false, label = "Loop" })
node:input("trigger", "boolean", { default = false })
node:input("event", "event", { required = false, default = 0 })
node:on_run(function(ctx)
    local active = ctx:input("trigger") or ctx:input("event") ~= 0
    if active then
        local speaker = ctx:endpoint("computed:speaker", ctx:field("face"))
        speaker:call("set_loop", ctx:field("loop"))
        speaker:call("play")
    end
end)

return node
