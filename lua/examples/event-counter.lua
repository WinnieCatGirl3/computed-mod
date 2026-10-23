local node = computed.node(1, "example:event_counter", "Event Counter")

node:category("state")
node:output("count", "number")
node:state("count", 0)
node:execution("event")
node:on_event("increment", function(ctx, amount)
    local next = ctx:state("count") + amount
    ctx:set_state("count", next)
    ctx:output("count", next)
end)

return node
