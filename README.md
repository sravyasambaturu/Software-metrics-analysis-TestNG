# Software Quality Metrics Analysis – TestNG

This repository contains datasets and analysis notebooks for a case study on the evolution of software quality metrics in the open-source Java project **TestNG**.

## 📁 Contents

- **TestNG Versions (5.13 to 7.11):**  
  Source code for five versions of TestNG used for metric extraction and analysis.
  
- **Excel Dataset:**  
  Software metrics extracted using the **Understand** static analysis tool.

- **Jupyter Notebooks:**  
  - `Wilcoxon_Test_For_ALL_Metrics_First_Last_Versions.ipynb`  
    Statistical comparison of key metrics between the earliest and latest versions.
    
  - `Wilcoxon_Test_For_ALL_Versions_LOC.ipynb`  
    Detailed LOC (Lines of Code) analysis across all version combinations.

  - `metrics.ipynb`  
    General metric exploration and visualizations.

## 📊 Metrics

Metrics analyzed include:
- LOC (Lines of Code)
- Cyclomatic Complexity
- Coupling (CBO), Cohesion (LCOM)
- Inheritance (DIT, NOC)
- Method and variable counts

All metrics were extracted using the **Understand** tool and stored in `Understand_Tool_Extracted_Metrics.xlsx`.

## 🔗 DOI

This dataset and analysis are archived at Zenodo:  
📄 [https://doi.org/10.5281/zenodo.15539320](https://doi.org/10.5281/zenodo.15539320)

## 🛠 Tools Used

- [Understand](https://scitools.com/) (for static code analysis)
- Python + Pandas + SciPy (for statistical tests)
- Jupyter Notebooks
- ChatGPT (for grammar and clarity refinement)

---

© 2025 | Research project on software quality evolution using static code metrics.
