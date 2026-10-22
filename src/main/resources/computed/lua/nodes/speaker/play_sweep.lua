local node = computed.node(1, "computed:speaker_play_sweep", "Speaker Play Sweep")

node:category("speaker")
node:style("sink")
node:execution("input")
node:field("face", "direction", { default = "front", label = "Face" })
node:field("wave", "choice", {
    default = "sine",
    label = "Waveform",
    choices = { "sine", "square", "saw", "triangle", "noise" }
})
node:field("frequency_from", "number", { default = 220, min = 55, max = 3520, step = 1, label = "From (Hz)" })
node:field("frequency_to", "number", { default = 880, min = 55, max = 3520, step = 1, label = "To (Hz)" })
node:field("duration", "number", { default = 2.0, min = 0.2, max = 10.0, step = 0.1, label = "Duration (s)" })
node:field("volume", "number", { default = 1.0, min = 0.0, max = 3.0, step = 0.1, label = "Volume" })
node:input("trigger", "boolean", { default = false })
node:input("event", "event", { required = false, default = 0 })
node:on_run(function(ctx)
    local active = ctx:input("trigger") or ctx:input("event") ~= 0
    if active then
        ctx:endpoint("computed:speaker", ctx:field("face")):call(
            "play_sweep",
            ctx:field("wave"),
            ctx:field("frequency_from"),
            ctx:field("frequency_to"),
            ctx:field("duration"),
            ctx:field("volume"))
    end
end)

return node
