local node = computed.node(1, "computed:threshold_trigger", "Threshold Trigger")

node:category("logic")
node:field("mode", "choice", {
    default = "above",
    label = "Trigger when",
    choices = { "above", "below" }
})
node:field("threshold", "number", {
    default = 0,
    min = -4096,
    max = 4096,
    label = "Threshold",
    step = 0.1
})
node:field("hysteresis", "number", {
    default = 0,
    min = 0,
    max = 10,
    label = "Hysteresis",
    control = "slider",
    step = 0.1
})
node:state("latched", false)
node:input("value", "number", { default = 0 })
node:output("triggered", "boolean")
node:on_run(function(ctx)
    local value = ctx:input("value") or 0
    local threshold = ctx:field("threshold")
    local hysteresis = ctx:field("hysteresis") or 0
    local latched = ctx:state("latched")
    if ctx:field("mode") == "below" then
        if value <= threshold then
            latched = true
        elseif value > threshold + hysteresis then
            latched = false
        end
    else
        if value >= threshold then
            latched = true
        elseif value < threshold - hysteresis then
            latched = false
        end
    end
    ctx:set_state("latched", latched)
    ctx:output("triggered", latched)
end)

return node
