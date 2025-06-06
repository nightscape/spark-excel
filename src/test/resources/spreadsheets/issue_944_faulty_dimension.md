The issue_944_faulty_dimension.xlsx file contains `<dimension>` tags on each sheet, that does not conform to the true / physical size of the sheets (e.g.  `<dimension ref="A1"/>` instead of `<dimension ref="A1:E2"/>` for sheet1).

It was fabricated by hand and is used to test the library's ability to handle such cases. 

This is how the file was created:
* take a valid excel file
* rename extension from xlsx to zip
* unzip it
* patch the `<dimension>` tags in `xl/worksheets/sheet1.xml` and `xl/worksheets/sheet2.xml`
* zip it back
* rename extension back to xlsx
