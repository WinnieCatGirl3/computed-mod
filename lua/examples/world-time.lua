local node = computed.node(1, "example:world_time", "World Time")

node:category("world")
node:style("source")
node:output("time", "number")
node:execution("tick")
node:on_run(function(ctx)
    ctx:output("time", ctx:endpoint("computed:world"):call("time"))
end)

return node
