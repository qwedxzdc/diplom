package com.arkell.util

import java.io.File
import java.util.*
import java.util.stream.Stream

object DocsCreator {

	

	fun getDocs(path: String, delimiter: String = "\n"): ClassDeclaration {
		return getDocs(File(path), delimiter)
	}

	fun getDocs(file: File, delimiter: String = "\n"): ClassDeclaration {
		//	dir.readText().lines().forEach { it.print("--") }
		var stream = file.readText().lines()

		// step 1 - search class

		stream = stream
			.dropWhile { it.startsWithAnyOf("package") || it.startsWithAnyOf("import") || it.trim() == "" }

		val lines = mutableListOf<String>()

		stream.forEach { lines.add(it.trim()) }

		val clazz = ClassDeclaration(lines
			.takeWhile { it.startsWithAnyOf("/", "*", "@", "class") }
			.toMutableList())

		//	stream.forEach { it.print() }

		val functionSignatures = mutableListOf<FunctionDeclaration>()

		for (i in 0 until lines.size) {
			if (lines[i].startsWithAnyOf("override fun ", "suspend fun ", "fun ")) {
				//			"------------".print()
				functionSignatures.add(
						FunctionDeclaration(getFunctionDocs(lines, i), delimiter))
			}
		}

		clazz.methods = functionSignatures.filter { it.path.isNotEmpty() }.toMutableList()

		//		Gson().toJson(clazz.apply { this.methods = functionSignatures }).print()
		return clazz
	}

	fun getFunctionDocs(lines: List<String>, functionIndex: Int): List<String> {

		val ret = LinkedList<String>()

		for (i in functionIndex - 1 downTo 0) {
			if (lines[i].trim().startsWithAnyOf("/", "*", "@")) {
				ret.push(lines[i].trim())
			} else if (lines[i].isBlank()) {
				// nothing
			} else {
				break
			}
		}
		var scopeLevel = 0
		val textLine = StringBuilder()
		var lineIndex = functionIndex
		do {
			textLine.append(lines[lineIndex++])
			scopeLevel = textLine.count { it == '(' } - textLine.count { it == ')' }
		} while (scopeLevel > 0)
		return ret.apply { this.add(textLine.toString().substringAfterEqualize('(', ')')) }
			.also {

				fun test(it: String): Boolean {
					return it.contains("):") && it.substringAfter("):").contains('{')
				}

				if (lines[lineIndex].let { test(it) }) {
					it.add(':' + lines[lineIndex]
						.substringAfter("):").substringBefore('{').trim() + '{')
				} else if (lines[lineIndex - 1].let { test(it) }) {
					it.add(':' + lines[lineIndex - 1]
						.substringAfter("):").substringBefore('{').trim() + '{')
				}

			}
	}

	private fun String.substringAfterEqualize(a: Char, b: Char): String {
		var diff = 0
		var ptr = this.indexOfFirst { it == a || it == b }
		if (ptr < 0) {
			return this
		}
		do {
			if (this[ptr] == a) {
				diff++
			} else if (this[ptr] == b) {
				diff--
			}
			ptr++
		} while (diff != 0)
		return this.substring(0 until ptr)
	}

	class FunctionDeclaration(lines: List<String>, delimiter: String) {

		var name: String = ""
		var args = mutableListOf<FunctionArg>()
		var method = "Any"
		var path = ""
		var docs = ""
		var returns = ""
		var returnType = ""
		var returnCodes = mutableMapOf<Int, String>()

		init {

			//			"-------------------".print()
			//			lines.forEach { it.print() }

			lines.find { it.startsWithAnyOf("override fun", "suspend", "fun") }
				?.apply {
					name = this.substringAfter("fun").substringBefore("(").trim()
					this.substringAfter('(')
						.split(',')
						.forEach {
							args.add(FunctionArg(it.trim()))
							args = args.filter { it.name != ")" }.toMutableList()
							//						it.print()
						}
				}

			if (lines.last().contains(':') && lines.last().contains('{')) {
				this.returnType = lines.last().substringAfter(':').substringBefore('{')
			}

			processAnnotations(lines.filter { it.trim().startsWith("@") })
			processJavaDoc(lines.filter { it.startsWithAnyOf("/*", "*") }, delimiter)

		}

		private fun processJavaDoc(lines_: List<String>, delimiter: String) {
			val lines = lines_
				.stream()
				.map { if (it.startsWith("/*")) it.substring(2) else it }
				.map { if (it.startsWith("*")) it.substring(1) else it }
				.map { it.trim() }
				.filter { it.any { it.isLetterOrDigit() } }


			var target = ""

			for (line in lines) {
				val select = if (line.startsWith("@")) {
					when (line.split(' ')[0]) {
						"@param" -> {
							//						println("!!!@Param!")
							line.substringAfter(' ')
								.apply { target = this.split(' ')[0] }
								.substringAfter(' ')
						}
						"@return" -> {
							target = "return"
							line.substringAfter(' ')
						}
						else -> {
							line
						}
					}
				} else {
					val code = line.split(' ')[0].toIntOrNull()
					if (target.startsWith("return") &&
							code != null) {
						target = "return-$code"
						line.substringAfter(code.toString()).drop(1)
					} else {
						line
					}
				}

				addDocs(target, select, delimiter)

				//			println("$target !!!! $select")
			}

			this.docs.removePrefix(delimiter)
			this.docs.removeSuffix(delimiter)
			this.returns.removePrefix(delimiter)
			this.returns.removeSuffix(delimiter)
		}

		private fun addDocs(target: String, line: String, delimiter: String) {
			if (target == "") {
				this.docs += line.trim() + delimiter
			} else if (target.startsWith("return")) {
				val code = target.substringAfter('-', "").toIntOrNull()
				if (code != null) {
					val prev = this.returnCodes[code] ?: ""
					this.returnCodes[code] = prev + line.trim() + delimiter
				} else {
					this.returns += line.trim() + delimiter
					val prev = this.returnCodes[200] ?: ""
					this.returnCodes[200] = prev + line.trim() + delimiter
				}
			} else {
				try {
					(this.args.find { it.name == target } ?: throw IllegalArgumentException())
						.apply {
							if (this.docs == null) {
								this.docs = line
							} else {
								this.docs += "$delimiter$line"
							}
						}
				} catch (e: Exception) {
					// why? =(
				}
			}
		}

		private fun processAnnotations(lines: List<String>) {
			lines.forEach {
				when {
					it.startsWithAnyOf("@GetMapping", "@PostMapping", "@DeleteMapping",
							"@RequestMapping") -> processPathAnnotation(it)
				}
			}
		}

		private fun processPathAnnotation(context: String) {
			if (context.startsWith("@GetMapping", true)) {
				this.method = "Get"
			} else if (context.startsWith("@PostMapping", true)) {
				this.method = "Post"
			} else if (context.startsWith("@DeleteMapping", true)) {
				this.method = "Delete"
			}
			this.path = context.substringAfter("(\"").substringBefore("\")")
		}

		data class FunctionArg(var name: String, var type: String, var clazz: String,
		                       var required: Boolean, var docs: String? = null) {

			constructor(signature: String) : this(
					name = signature.substringBefore(':').substringAfterLast(' ').trim(),
					type = getArgAnnotation(signature),
					clazz = getClass(signature),
					required = true) {
				if (clazz.endsWith("?") ||
						type.contains("defaultvalue", true) ||
						type.contains("required = false", true)) {
					required = false
				}
				this.type = this.type.substringBefore('(').substringBefore(' ')
				this.clazz = this.clazz.filter { it != '?' }
			}

			companion object {
				fun getClass(string: String): String =
						string.substringAfter(':').trim().substringBefore(' ').filter { it != ')' }

				fun getArgAnnotation(string: String): String {
					return if (string.contains('(')) {
						string.substringBefore(')') + ')'
					} else {
						string.split(" ")[0]
					}
				}
			}
		}
	}

	class ClassDeclaration(list: MutableList<String>) {


		var name = ""
		var description = ""
		var path = "/"
		var methods = mutableListOf<FunctionDeclaration>()

		init {
			list.find { it.startsWith("class") }?.apply {
				name = this.substringAfter("class")
					.substringBefore('(')
					.substringBefore("{")
					.trim()
			}
			list.removeIf { it.startsWith("class") }
			list.find { it.trim().startsWith("@RequestMapping(") }
				?.apply { path = this.substringAfter('"').substringBefore('"') }
			val javaDocList = list.stream()
				.filter { it.startsWith("/*") || it.startsWith("*") }
				.filter { it != "*/" }
				.map {
					it.removePrefix("/").removePrefix("*")
				}
				//				.peek { it.print() }
				.toList()
			for (javaDocLine in javaDocList) {
				var line = javaDocLine.trim()
				if (line.startsWith('*')) {
					try {
						line = line.substring(2)
					} catch (e: Exception) {
					}
				}
				//				line.print()
				description += "${line.removePrefix("*").trim()} "
			}

			description = description.trim()
		}

	}

	private fun <T> Stream<T>.toList(): MutableList<T> {
		val ret = mutableListOf<T>()
		this.forEach { ret.add(it) }
		return ret
	}

	private fun String.startsWithAnyOf(vararg lexems: String): Boolean {
		for (it in lexems) {
			if (this.startsWith(it)) {
				return true
			}
		}
		return false
	}
}