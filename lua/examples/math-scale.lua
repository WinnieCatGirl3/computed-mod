local node = computed.node(1, "example:math_scale", "Scale")

node:category("math")
node:input("value", "number", { default = 0 })
node:field("factor", "number", { default = 2 })
node:output("result", "number")
node:on_run(function(ctx)
    ctx:output("result", ctx:input("value") * ctx:field("factor"))
end)

return node
