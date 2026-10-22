local node = computed.node(1, "computed:block_presence", "Block Presence")

node:category("world")
node:style("source")
node:execution("tick")
node:field("face", "direction", { default = "front" })
node:output("present", "string")
node:on_run(function(ctx)
    local present = ctx:endpoint("computed:world"):call("block_present", ctx:field("face"))
    ctx:output("present", present and "present" or "absent")
end)

return node
