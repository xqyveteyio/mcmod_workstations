plugins {
	id("dev.kikugie.stonecutter")
}

stonecutter active "1.20.1"

stonecutter parameters {
	swaps["minecraft"] = "\"${node.metadata.version}\";"
}
