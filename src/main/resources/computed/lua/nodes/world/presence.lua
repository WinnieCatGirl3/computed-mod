local node = computed.node(1, "computed:block_presence", "Block Presence")

node:category("world")
node:style("source")
node:execution("tick")
node:field("face", "direction", { default = "front" })
node:output("present", "string")
node:output("block", "string")
node:on_run(function(ctx)
    local world = ctx:endpoint("computed:world")
    local present = world:call("block_present", ctx:field("face"))
    ctx:output("present", present and "present" or "absent")
    ctx:output("block", world:call("block_id", ctx:field("face")))
end)

return node
