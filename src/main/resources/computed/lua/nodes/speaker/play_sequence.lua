local node = computed.node(1, "computed:speaker_play_sequence", "Speaker Play Sequence")

node:category("speaker")
node:style("sink")
node:execution("input")
node:field("face", "direction", { default = "front", label = "Face" })
node:field("sequence", "text", {
    default = "harp:12:0.5 bell:18:0.5 pling:15:0.5",
    label = "Sequence (note:seconds steps)"
})
node:input("trigger", "boolean", { default = false })
node:input("event", "event", { required = false, default = 0 })
node:on_run(function(ctx)
    local active = ctx:input("trigger") or ctx:input("event") ~= 0
    if active then
        ctx:endpoint("computed:speaker", ctx:field("face")):call(
            "play_sequence",
            ctx:field("sequence"))
    end
end)

return node
