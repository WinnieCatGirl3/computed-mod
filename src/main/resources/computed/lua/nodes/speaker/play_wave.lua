local node = computed.node(1, "computed:speaker_play_wave", "Speaker Play Wave")

node:category("speaker")
node:style("sink")
node:execution("input")
node:field("face", "direction", { default = "front", label = "Face" })
node:field("wave", "choice", {
    default = "sine",
    label = "Waveform",
    choices = { "sine", "square", "saw", "triangle", "noise" }
})
node:field("frequency", "number", { default = 440, min = 55, max = 3520, step = 1, label = "Frequency (Hz)" })
node:field("volume", "number", { default = 1.0, min = 0.0, max = 3.0, step = 0.1, label = "Volume" })
node:field("attack", "number", { default = 0.0, min = 0.0, max = 2.0, step = 0.05, label = "Attack (s)" })
node:field("decay", "number", { default = 0.0, min = 0.0, max = 2.0, step = 0.05, label = "Decay (s)" })
node:input("trigger", "boolean", { default = false })
node:input("event", "event", { required = false, default = 0 })
node:on_run(function(ctx)
    local active = ctx:input("trigger") or ctx:input("event") ~= 0
    if active then
        ctx:endpoint("computed:speaker", ctx:field("face")):call(
            "play_wave",
            ctx:field("wave"),
            ctx:field("frequency"),
            ctx:field("volume"),
            ctx:field("attack"),
            ctx:field("decay"))
    end
end)

return node
