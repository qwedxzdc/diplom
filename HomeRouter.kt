package com.arkell.config

import com.arkell.util.DocsCreator
import com.arkell.util.EntityDocsCreator
import com.arkell.util.SwaggerGen
import org.apache.tomcat.util.http.fileupload.IOUtils
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.ui.set
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import java.io.File
import java.io.FileInputStream
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

@Controller
@RequestMapping("/")
class HomeRouter {

	private val resourceDir = System.getProperty("user.dir") + "/src/main/resources/static"

	//	@RequestMapping("/")
	//	fun homeRoute(response: HttpServletResponse) {
	//		dispatch("/index.html", response)
	//	}

	@GetMapping("/docs")
	fun test(model: Model): String {
		model["apis"] = File(System.getProperty("user.dir") + "/src/main/java/com/arkell/api").listFiles()
			.filter { it.name != "AbstractAPI.kt" }
			.flatMap { if (it.isFile) listOf(it) else it.listFiles().toList() }
			.map { DocsCreator.getDocs(it, "<br>") }
		return "docs-methods"
	}

	@GetMapping("/swagger")
	fun swagger(): ResponseEntity<*> {
		return ResponseEntity.ok(
				SwaggerGen.getSwaggerDoc(*File(System.getProperty("user.dir") + "/src/main/java/com/arkell/api").listFiles()
					.filter { it.name != "AbstractAPI.kt" }
					.flatMap { if (it.isFile) listOf(it) else it.listFiles().toList() }
					.map { DocsCreator.getDocs(it, " ") }.toTypedArray()
				)
		)
	}

	@GetMapping("/docs/entity")
	fun docsEntity(model: Model): String {
		val names = File(System.getProperty("user.dir") + "/src/main/java/com/arkell/api").listFiles()
			.filter { it.name != "AbstractAPI.kt" }
			.flatMap { if (it.isFile) listOf(it) else it.listFiles().toList() }
			.map { DocsCreator.getDocs(it, "<br>") }
			.flatMap { it.methods }
			.map { it.returnType }
		model["entities"] = EntityDocsCreator.test(names.toSet())
		return "docs-entity"
	}

	// sendMessage functions below

	@GetMapping("/sendMessage")
	fun test(request: HttpServletRequest, response: HttpServletResponse) {
		request.reader.lines().forEach { println(it) }
	}

	//	@RequestMapping("/**")
	//	fun router(request: HttpServletRequest, response: HttpServletResponse) {
	//		dispatch(request.requestURI, response)
	//	}

	private fun dispatch(path: String, response: HttpServletResponse) {
		response.characterEncoding = "UTF-8"
		try {
			IOUtils.copy(FileInputStream(resourceDir + path), response.outputStream)
		} catch (e: Exception) {
			response.writer.write("404: File not found")
		}
	}

}




