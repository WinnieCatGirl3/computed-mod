local node = computed.node(1, "computed:speaker_status", "Speaker Status")

node:category("speaker")
node:style("source")
node:execution("tick")
node:field("face", "direction", { default = "front", label = "Face" })
node:output("has_disc", "boolean")
node:output("is_playing", "boolean")
node:output("is_looping", "boolean")
node:output("queued", "number")
node:on_run(function(ctx)
    local speaker = ctx:endpoint("computed:speaker", ctx:field("face"))
    ctx:output("has_disc", speaker:call("has_disc"))
    ctx:output("is_playing", speaker:call("is_playing"))
    ctx:output("is_looping", speaker:call("is_looping"))
    ctx:output("queued", speaker:call("queue_size"))
end)

return node
