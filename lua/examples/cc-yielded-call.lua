local node = computed.node(1, "example:cc_yielded_call", "CC Yielded Call")

node:category("integration/computercraft")
node:execution("tick")
node:field("side", "direction", { default = "left" })
node:field("method", "text", { default = "getEnergy" })
node:output("result", "table")
node:on_run(function(ctx)
    local peripheral = ctx:endpoint("computercraft:peripheral", ctx:field("side"))
    ctx:output("result", peripheral:call(ctx:field("method")))
end)

return node
