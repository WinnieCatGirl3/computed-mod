local node = computed.node(1, "computed:aeronautics_altitude", "Altitude")

node:category("integration/aeronautics/telemetry/position")
node:style("source")
node:execution("tick")
node:field("face", "direction", { default = "front", label = "Face" })
node:output('altitude', 'number')
node:on_run(function(ctx)
    local data = ctx:endpoint("aeronautics:telemetry", ctx:field("face")):call("measure")
    ctx:output('altitude', data.y)
end)

return node
