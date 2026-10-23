local node = computed.node(1, "example:guarded_divide", "Guarded Divide")

node:category("math")
node:input("a", "number")
node:input("b", "number")
node:output("result", "number")
node:on_run(function(ctx)
    if ctx:input("b") == 0 then
        error("division by zero")
    end
    ctx:output("result", ctx:input("a") / ctx:input("b"))
end)

return node
