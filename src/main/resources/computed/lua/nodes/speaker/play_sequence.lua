local node = computed.node(1, "computed:speaker_play_sequence", "Speaker Play Sequence")

node:category("speaker")
node:style("sink")
node:execution("input")
node:field("face", "direction", { default = "front", label = "Face" })
node:input("trigger", "boolean", { default = false })
node:input("event", "event", { required = false, default = 0 })
for index = 1, 16 do
    node:input("note_" .. index, "string", { required = false, default = "" })
end
node:on_run(function(ctx)
    local active = ctx:input("trigger") or ctx:input("event") ~= 0
    if not active then
        return
    end
    local inputs = ctx:inputs()
    local steps = {}
    for index = 1, 16 do
        local note = inputs["note_" .. index]
        if note ~= nil and note ~= "" then
            steps[#steps + 1] = note
        end
    end
    if #steps > 0 then
        ctx:endpoint("computed:speaker", ctx:field("face")):call(
            "play_sequence",
            table.concat(steps, " "))
    end
end)

return node
