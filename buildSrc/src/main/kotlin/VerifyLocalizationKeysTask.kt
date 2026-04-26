import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

@DisableCachingByDefault(because = "Checks localization key parity by reading XML files")
abstract class VerifyLocalizationKeysTask : DefaultTask() {

    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val resourcesDir: DirectoryProperty

    @TaskAction
    fun verify() {
        val resourcesRoot = resourcesDir.get().asFile
        val baseFolder = File(resourcesRoot, "values")
        require(baseFolder.exists()) {
            "Missing base resources folder: ${baseFolder.absolutePath}"
        }

        val base = collectSnapshot(baseFolder)
        val localeFolders = resourcesRoot
            .listFiles { file -> file.isDirectory && isLocaleValuesFolder(file.name) }
            .orEmpty()
            .sortedBy { it.name }

        if (localeFolders.isEmpty()) {
            logger.lifecycle("No localized values-* folders found under ${resourcesRoot.absolutePath}")
            return
        }

        val errors = mutableListOf<String>()
        for (localeFolder in localeFolders) {
            val locale = collectSnapshot(localeFolder)

            val missingStrings = (base.strings - locale.strings).sorted()
            val extraStrings = (locale.strings - base.strings).sorted()
            val missingPlurals = (base.plurals.keys - locale.plurals.keys).sorted()
            val extraPlurals = (locale.plurals.keys - base.plurals.keys).sorted()

            if (missingStrings.isNotEmpty()) {
                errors += "${localeFolder.name}: missing <string> keys: ${missingStrings.joinToString(", ")}"
            }
            if (extraStrings.isNotEmpty()) {
                errors += "${localeFolder.name}: extra <string> keys: ${extraStrings.joinToString(", ")}"
            }
            if (missingPlurals.isNotEmpty()) {
                errors += "${localeFolder.name}: missing <plurals> keys: ${missingPlurals.joinToString(", ")}"
            }
            if (extraPlurals.isNotEmpty()) {
                errors += "${localeFolder.name}: extra <plurals> keys: ${extraPlurals.joinToString(", ")}"
            }

            val sharedPluralKeys = base.plurals.keys.intersect(locale.plurals.keys).sorted()
            for (key in sharedPluralKeys) {
                val baseQuantities = base.plurals.getValue(key)
                val localeQuantities = locale.plurals.getValue(key)
                if (baseQuantities != localeQuantities) {
                    val missingQuantities = (baseQuantities - localeQuantities).sorted()
                    val extraQuantities = (localeQuantities - baseQuantities).sorted()
                    if (missingQuantities.isNotEmpty()) {
                        errors += "${localeFolder.name}: <plurals name=\"$key\"> missing quantities: ${missingQuantities.joinToString(", ")}"
                    }
                    if (extraQuantities.isNotEmpty()) {
                        errors += "${localeFolder.name}: <plurals name=\"$key\"> extra quantities: ${extraQuantities.joinToString(", ")}"
                    }
                }
            }
        }

        if (errors.isNotEmpty()) {
            throw GradleException(
                buildString {
                    appendLine("Localization key parity check failed.")
                    appendLine("Base folder: ${baseFolder.absolutePath}")
                    appendLine()
                    errors.forEach { appendLine("- $it") }
                }
            )
        }
    }

    private fun collectSnapshot(valuesFolder: File): ResourceSnapshot {
        val strings = mutableSetOf<String>()
        val plurals = linkedMapOf<String, MutableSet<String>>()

        valuesFolder
            .listFiles { file -> file.isFile && file.extension.equals("xml", ignoreCase = true) }
            .orEmpty()
            .sortedBy { it.name }
            .forEach { file ->
                parseXmlFile(file, strings, plurals)
            }

        return ResourceSnapshot(
            strings = strings,
            plurals = plurals.mapValues { it.value.toSet() }
        )
    }

    private fun parseXmlFile(
        file: File,
        strings: MutableSet<String>,
        plurals: MutableMap<String, MutableSet<String>>
    ) {
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = false
            setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
            setFeature("http://xml.org/sax/features/external-general-entities", false)
            setFeature("http://xml.org/sax/features/external-parameter-entities", false)
            setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
        }
        val document = factory.newDocumentBuilder().parse(file)
        document.documentElement.normalize()

        val stringNodes = document.getElementsByTagName("string")
        for (i in 0 until stringNodes.length) {
            val node = stringNodes.item(i)
            if (node is Element) {
                val name = node.getAttribute("name").trim()
                if (name.isNotEmpty()) {
                    strings += name
                }
            }
        }

        val pluralNodes = document.getElementsByTagName("plurals")
        for (i in 0 until pluralNodes.length) {
            val node = pluralNodes.item(i)
            if (node !is Element) continue
            val key = node.getAttribute("name").trim()
            if (key.isEmpty()) continue

            val quantities = plurals.getOrPut(key) { linkedSetOf() }
            val itemNodes = node.getElementsByTagName("item")
            for (j in 0 until itemNodes.length) {
                val item = itemNodes.item(j)
                if (item is Element) {
                    val quantity = item.getAttribute("quantity").trim()
                    if (quantity.isNotEmpty()) {
                        quantities += quantity
                    }
                }
            }
        }
    }

    private fun isLocaleValuesFolder(folderName: String): Boolean {
        if (!folderName.startsWith("values-")) return false
        val firstQualifier = folderName.removePrefix("values-").substringBefore('-')
        return firstQualifier.matches(Regex("[a-z]{2,3}"))
    }

    private data class ResourceSnapshot(
        val strings: Set<String>,
        val plurals: Map<String, Set<String>>
    )
}
