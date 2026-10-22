local node = computed.node(1, "computed:display_present", "Display Present")

node:category("world")
node:style("source")
node:execution("tick")
node:field("face", "direction", { default = "front" })
node:output("attached", "boolean")
node:output("present", "string")
node:on_run(function(ctx)
    local attached = ctx:endpoint("computed:monitor", ctx:field("face")):call("has_display")
    ctx:output("attached", attached)
    ctx:output("present", attached and "present" or "absent")
end)

return node
