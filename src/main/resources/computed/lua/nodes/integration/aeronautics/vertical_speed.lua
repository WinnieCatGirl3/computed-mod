local node = computed.node(1, "computed:aeronautics_vertical_speed", "Vertical Speed")

node:category("integration/aeronautics/telemetry/velocity")
node:style("source")
node:execution("tick")
node:field("face", "direction", { default = "front", label = "Face" })
node:output('vertical_speed', 'number')
node:on_run(function(ctx)
    local data = ctx:endpoint("aeronautics:telemetry", ctx:field("face")):call("measure")
    ctx:output('vertical_speed', data.vy)
end)

return node
